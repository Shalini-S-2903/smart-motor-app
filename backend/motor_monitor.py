"""
Motor safety monitoring engine.
Background task: tracks last_state, running_since, recent_power_samples per device.
Rules: DRY_RUN, MAX_RUNTIME, SMART_FILL. Auto-issues OFF via MQTT; does not change protocol.
"""

import logging
import threading
import time
from collections import deque
from datetime import datetime, timezone
from typing import Any, Callable, Dict, Optional

from db import SessionLocal
from models import Device, DeviceEvent

logger = logging.getLogger("motor_monitor")

DEFAULT_MAX_RUNTIME_MINUTES = 20
DRY_RUN_AVG_W_THRESHOLD = 40.0
DRY_RUN_WINDOW_SEC = 15
POWER_SAMPLES_WINDOW_SEC = 30
SMART_FILL_STABLE_SEC = 10
SMART_FILL_DROP_RATIO = 0.4  # trigger if power < 40% of stable avg
CHECK_INTERVAL_SEC = 5


class MotorMonitor:
    """
    Tracks per device: last_state, running_since, recent_power_samples (last 30s).
    Runs a background thread that evaluates DRY_RUN, MAX_RUNTIME, SMART_FILL and
    publishes OFF when needed; stores events in device_events.
    """

    def __init__(
        self,
        mqtt_service: Any,
        on_event_emitted: Optional[Callable[[str, str, datetime], None]] = None,
    ) -> None:
        self._mqtt = mqtt_service
        self._on_event_emitted = on_event_emitted
        self._lock = threading.Lock()
        self._devices: Dict[str, Dict[str, Any]] = {}  # device_id -> { last_state, running_since, power_samples }
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None

    def on_state_power(self, device_id: str, state: Optional[str], power: Optional[float], now_ts: float) -> None:
        """Call from MQTT when state or power is updated. Keeps logic fast for MQTT thread."""
        with self._lock:
            d = self._devices.setdefault(device_id, {
                "last_state": None,
                "running_since": None,
                "power_samples": deque(maxlen=120),
            })
            prev_state = d["last_state"]
            if state is not None:
                state_upper = state.strip().upper() if isinstance(state, str) else None
                d["last_state"] = state_upper
                if state_upper == "ON":
                    if d["running_since"] is None:
                        d["running_since"] = now_ts
                else:
                    d["running_since"] = None
            if power is not None:
                d["power_samples"].append((now_ts, power))
            # Trim to last 30 seconds
            while d["power_samples"] and (now_ts - d["power_samples"][0][0]) > POWER_SAMPLES_WINDOW_SEC:
                d["power_samples"].popleft()

    def start(self) -> None:
        self._stop.clear()
        self._thread = threading.Thread(target=self._loop, name="motor-monitor", daemon=True)
        self._thread.start()
        logger.info("MotorMonitor started")

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=10)
            self._thread = None
        logger.info("MotorMonitor stopped")

    def _loop(self) -> None:
        while not self._stop.is_set():
            try:
                self._check_rules()
            except Exception:
                logger.exception("MotorMonitor rule check error")
            self._stop.wait(CHECK_INTERVAL_SEC)

    def _check_rules(self) -> None:
        now = time.time()
        with self._lock:
            devices_snapshot = {
                did: {
                    "last_state": d["last_state"],
                    "running_since": d["running_since"],
                    "power_samples": list(d["power_samples"]),
                }
                for did, d in self._devices.items()
            }
        for device_id, data in devices_snapshot.items():
            if data["last_state"] != "ON":
                continue
            running_since = data["running_since"]
            if running_since is None:
                continue
            power_samples = data["power_samples"]

            reason = self._eval_dry_run(now, power_samples)
            if reason:
                self._trigger_off(device_id, reason, now)
                self._clear_running_since(device_id)
                continue

            reason = self._eval_max_runtime(device_id, now, running_since)
            if reason:
                self._trigger_off(device_id, reason, now)
                self._clear_running_since(device_id)
                continue

            reason = self._eval_smart_fill(device_id, now, running_since, power_samples)
            if reason:
                self._trigger_off(device_id, reason, now)
                self._clear_running_since(device_id)

    def _eval_dry_run(self, now: float, power_samples: list) -> Optional[str]:
        """If ON and avg power < 40W for 15s -> DRY_RUN."""
        recent = [(t, p) for t, p in power_samples if (now - t) <= DRY_RUN_WINDOW_SEC]
        if len(recent) < 3:
            return None
        avg = sum(p for _, p in recent) / len(recent)
        if avg < DRY_RUN_AVG_W_THRESHOLD:
            return "DRY_RUN"
        return None

    def _eval_max_runtime(self, device_id: str, now: float, running_since: float) -> Optional[str]:
        """If running time > user_limit_minutes (default 20) -> SAFETY_TIMER."""
        limit_minutes = self._get_max_runtime_minutes(device_id)
        if (now - running_since) > limit_minutes * 60:
            return "SAFETY_TIMER"
        return None

    def _get_max_runtime_minutes(self, device_id: str) -> int:
        try:
            db = SessionLocal()
            dev = db.get(Device, device_id)
            out = int(dev.max_runtime_minutes) if dev and dev.max_runtime_minutes is not None else DEFAULT_MAX_RUNTIME_MINUTES
            db.close()
            return out
        except Exception:
            return DEFAULT_MAX_RUNTIME_MINUTES

    def _eval_smart_fill(self, device_id: str, now: float, running_since: float, power_samples: list) -> Optional[str]:
        """If fill_mode and power drops by >60% after stable run -> TANK_FULL."""
        if not self._is_fill_mode(device_id):
            return None
        run_duration = now - running_since
        if run_duration < SMART_FILL_STABLE_SEC:
            return None
        # Stable window: [now - 20, now - 5] so we have 15s of “stable” history
        stable_end = now - 5
        stable_start = stable_end - 15
        stable = [p for t, p in power_samples if stable_start <= t <= stable_end]
        if len(stable) < 3:
            return None
        stable_avg = sum(stable) / len(stable)
        if stable_avg <= 0:
            return None
        recent = [p for t, p in power_samples if t >= now - 5]
        if len(recent) < 2:
            return None
        recent_avg = sum(recent) / len(recent)
        if recent_avg < SMART_FILL_DROP_RATIO * stable_avg:
            return "TANK_FULL"
        return None

    def _is_fill_mode(self, device_id: str) -> bool:
        try:
            db = SessionLocal()
            dev = db.get(Device, device_id)
            out = bool(dev and dev.fill_mode in ("1", "true", "yes", True))
            db.close()
            return out
        except Exception:
            return False

    def _trigger_off(self, device_id: str, reason: str, now_ts: float) -> None:
        """Publish OFF command and insert device_events row."""
        logger.info("MotorMonitor triggering OFF device_id=%s reason=%s", device_id, reason)
        self._mqtt.create_command(device_id, "OFF")
        self._insert_event(device_id, reason)

    def _insert_event(self, device_id: str, reason: str) -> None:
        try:
            db = SessionLocal()
            db.add(DeviceEvent(device_id=device_id, reason=reason))
            db.commit()
            when = datetime.now(tz=timezone.utc)
            if self._on_event_emitted:
                self._on_event_emitted(device_id, reason, when)
        except Exception:
            db.rollback()
            logger.exception("Failed to insert DeviceEvent device_id=%s reason=%s", device_id, reason)
        finally:
            db.close()

    def _clear_running_since(self, device_id: str) -> None:
        with self._lock:
            if device_id in self._devices:
                self._devices[device_id]["running_since"] = None
