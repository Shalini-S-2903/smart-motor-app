import json
import logging
import os
import threading
import time
import uuid
from datetime import datetime, timezone
from typing import Dict, Any

import paho.mqtt.client as mqtt

from db import SessionLocal
from models import Device, DeviceStateHistory, Command

logger = logging.getLogger("mqtt_service")


class MQTTService:
    """
    MQTT bridge:
    - Publishes commands to /devices/{deviceId}/command
    - Subscribes to:
        /devices/+/state
        /devices/+/power
        /devices/+/heartbeat
    - Maintains in-memory device state with last_seen + online flag.
    """

    def __init__(self, broker: str, port: int, username: str | None, password: str | None):
        self.broker = broker
        self.port = port
        self.username = username or None
        self.password = password or None

        self.client = mqtt.Client()
        if self.username and self.password:
            self.client.username_pw_set(self.username, self.password)

        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect

        self._lock = threading.Lock()
        self._states: Dict[str, Dict[str, Any]] = {}
        self._commands: Dict[str, Dict[str, Any]] = {}
        self.is_connected: bool = False

        self._stop_event = threading.Event()
        self._heartbeat_thread: threading.Thread | None = None
        self._commands_thread: threading.Thread | None = None

    @classmethod
    def from_env(cls) -> "MQTTService":
        broker = os.getenv("MQTT_BROKER", "broker.hivemq.com")
        port = int(os.getenv("MQTT_PORT", "1883"))
        username = os.getenv("MQTT_USERNAME", "") or None
        password = os.getenv("MQTT_PASSWORD", "") or None
        return cls(broker, port, username, password)

    # ---------- Lifecycle ----------
    def start(self) -> None:
        logger.info("Connecting to MQTT broker %s:%s", self.broker, self.port)
        self.client.connect(self.broker, self.port, keepalive=60)
        # start background network thread
        self.client.loop_start()
        # start heartbeat/online checker thread
        self._stop_event.clear()
        self._heartbeat_thread = threading.Thread(target=self._heartbeat_loop, name="mqtt-heartbeat", daemon=True)
        self._heartbeat_thread.start()
        # start command monitor thread
        self._commands_thread = threading.Thread(target=self._commands_loop, name="mqtt-commands", daemon=True)
        self._commands_thread.start()

    def stop(self) -> None:
        try:
            self._stop_event.set()
            if self._heartbeat_thread is not None:
                self._heartbeat_thread.join(timeout=5)
            if self._commands_thread is not None:
                self._commands_thread.join(timeout=5)
        except Exception:
            logger.exception("Error stopping heartbeat thread")
        try:
            self.client.loop_stop()
            self.client.disconnect()
        except Exception:
            logger.exception("Error stopping MQTT client")

    # ---------- Callbacks ----------
    def _on_connect(self, client, userdata, flags, rc):
        self.is_connected = rc == 0
        if rc == 0:
            logger.info("MQTT connected")
            # subscribe to wildcard topics
            client.subscribe("/devices/+/state")
            client.subscribe("/devices/+/power")
            client.subscribe("/devices/+/heartbeat")
            client.subscribe("/devices/+/ack")
        else:
            logger.error("MQTT connection failed rc=%s", rc)

    def _on_disconnect(self, client, userdata, rc):
        self.is_connected = False
        logger.warning("MQTT disconnected rc=%s", rc)

    def _on_message(self, client, userdata, msg):
        topic = msg.topic
        payload = msg.payload.decode("utf-8") if msg.payload else ""
        logger.info("MQTT message topic=%s payload=%s", topic, payload)

        # Topic format: /devices/{deviceId}/suffix
        parts = topic.split("/")
        if len(parts) < 4 or parts[1] != "devices":
            return
        device_id = parts[2]
        suffix = parts[3]

        now = time.time()
        with self._lock:
            state = self._states.setdefault(device_id, {})
            # heartbeat always updates last_seen
            if suffix == "heartbeat":
                state["last_seen_ts"] = now
                self._ensure_device_persisted(device_id)
                # status recalculation thread will set online based on last_seen_ts
            elif suffix == "state":
                state["last_state"] = payload
                self._insert_state_history(device_id, state_value=payload, power_value=state.get("last_power"))
            elif suffix == "power":
                try:
                    power_val = float(payload)
                    state["last_power"] = power_val
                except ValueError:
                    power_val = None
                    state["last_power"] = None
                self._insert_state_history(device_id, state_value=state.get("last_state"), power_value=power_val)
            elif suffix == "ack":
                # Example payload: {"command_id": "...", "status": "SUCCESS"}
                try:
                    data = json.loads(payload)
                except json.JSONDecodeError:
                    logger.warning("Invalid ACK payload JSON: %s", payload)
                    return
                cmd_id = data.get("command_id")
                if not cmd_id:
                    return
                cmd = self._commands.get(cmd_id)
                if cmd:
                    cmd["status"] = "SUCCESS"
                    cmd["updated_at_ts"] = now
                    self._update_command_status(cmd_id, "SUCCESS", now)

    def _heartbeat_loop(self) -> None:
        """Background loop: every 10s, recompute online/offline from last_seen_ts."""
        while not self._stop_event.is_set():
            now = time.time()
            with self._lock:
                for device_id, state in self._states.items():
                    ts = state.get("last_seen_ts")
                    if ts is None:
                        # never seen heartbeat → treat as offline by default
                        state["online"] = False
                    else:
                        state["online"] = (now - ts) <= 30.0
            self._stop_event.wait(10.0)

    def _commands_loop(self) -> None:
        """Background loop: every 5s, mark old pending commands as FAILED."""
        while not self._stop_event.is_set():
            now = time.time()
            with self._lock:
                for cmd_id, info in self._commands.items():
                    if info.get("status") == "PENDING":
                        created = info.get("created_at_ts", now)
                        if now - created > 10.0:
                            info["status"] = "FAILED"
                            info["updated_at_ts"] = now
            self._stop_event.wait(5.0)

    # ---------- Persistence helpers ----------
    def _ensure_device_persisted(self, device_id: str) -> None:
        """Create Device row on first heartbeat if it doesn't exist."""
        try:
            db = SessionLocal()
            obj = db.get(Device, device_id)
            if obj is None:
                db.add(Device(id=device_id))
            db.commit()
        except Exception:
            db.rollback()
            logger.exception("Failed to ensure Device(%s) in DB", device_id)
        finally:
            db.close()

    def _insert_state_history(self, device_id: str, state_value: str | None, power_value: float | None) -> None:
        try:
            db = SessionLocal()
            db.add(
                DeviceStateHistory(
                    device_id=device_id,
                    timestamp=datetime.now(tz=timezone.utc),
                    state=state_value,
                    power=power_value,
                )
            )
            db.commit()
        except Exception:
            db.rollback()
            logger.exception("Failed to insert DeviceStateHistory for %s", device_id)
        finally:
            db.close()

    def _insert_command(self, command_id: String | str, device_id: str, action: str, created_ts: float) -> None:
        try:
            db = SessionLocal()
            db.add(
                Command(
                    command_id=command_id,
                    device_id=device_id,
                    action=action,
                    status="PENDING",
                    created_at=datetime.fromtimestamp(created_ts, tz=timezone.utc),
                )
            )
            db.commit()
        except Exception:
            db.rollback()
            logger.exception("Failed to insert Command(%s)", command_id)
        finally:
            db.close()

    def _update_command_status(self, command_id: str, status: str, when_ts: float) -> None:
        try:
            db = SessionLocal()
            cmd = db.get(Command, command_id)
            if cmd is not None:
                cmd.status = status
                cmd.completed_at = datetime.fromtimestamp(when_ts, tz=timezone.utc)
                db.commit()
        except Exception:
            db.rollback()
            logger.exception("Failed to update Command(%s) status=%s", command_id, status)
        finally:
            db.close()

    # ---------- Public API ----------
    def snapshot_states(self) -> Dict[str, Dict[str, Any]]:
        with self._lock:
            # shallow copy for thread safety
            return {k: dict(v) for k, v in self._states.items()}

    def snapshot_states(self) -> Dict[str, Dict[str, Any]]:
        with self._lock:
            return {k: dict(v) for k, v in self._states.items()}

    def create_command(self, device_id: str, action: str) -> str:
        """
        Create a tracked command, publish to MQTT, and return command_id.
        MQTT payload:
          { "command_id": "...", "action": "ON" }
        """
        cmd_id = str(uuid.uuid4())
        now = time.time()
        with self._lock:
            self._commands[cmd_id] = {
                "device_id": device_id,
                "action": action,
                "status": "PENDING",
                "created_at_ts": now,
                "updated_at_ts": now,
            }
        self._ensure_device_persisted(device_id)
        self._insert_command(cmd_id, device_id, action, created_ts=now)

        topic = f"/devices/{device_id}/command"
        payload = json.dumps(
            {"command_id": cmd_id, "action": action}, separators=(",", ":")
        )
        logger.info("Publishing MQTT command topic=%s payload=%s", topic, payload)
        self.client.publish(topic, payload, qos=0, retain=False)
        return cmd_id

    def get_command(self, command_id: str) -> Dict[str, Any] | None:
        with self._lock:
            info = self._commands.get(command_id)
            return dict(info) if info else None



