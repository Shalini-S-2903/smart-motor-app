"""
FastAPI backend for personal IoT project.
No Tuya. Talks to private MQTT broker and ESP32 devices.
SSE at GET /events for real-time device updates.
"""

import asyncio
import json
from collections.abc import AsyncGenerator

from dotenv import load_dotenv
import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone
import time

from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from auth import ClaimRequest, get_current_user, router as auth_router
from db import get_db, init_db
from mqtt_service import MQTTService
from motor_monitor import MotorMonitor
from models import User, Device, DeviceOwnership, DevicePairingSession, DeviceEvent, UserActivity
from room_models import Room, RoomDevice, Schedule
from room_routes import router as room_routes
from alexa_routes import router as alexa_routes

# ---------------------------------------------------------------------------
# Device event reason -> human-readable message (never expose raw reason to frontend)
# ---------------------------------------------------------------------------

EVENT_REASON_TO_MESSAGE: dict[str, str] = {
    "DRY_RUN": "Motor stopped: No water detected",
    "SAFETY_TIMER": "Motor stopped: Max runtime reached",
    "TANK_FULL": "Tank filled successfully",
    "OFFLINE": "Device went offline",
    "ONLINE": "Device reconnected",
}


def _device_event_message(reason: str) -> str:
    return EVENT_REASON_TO_MESSAGE.get(reason, reason)


# ---------------------------------------------------------------------------
# Environment & Logging
# ---------------------------------------------------------------------------

load_dotenv()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("smartmotor-backend")

# ---------------------------------------------------------------------------
# App lifecycle (Render-safe)
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Ensure DB schema exists
    init_db()

    loop = asyncio.get_running_loop()
    event_queue: asyncio.Queue = asyncio.Queue()
    client_queues: set[asyncio.Queue] = set()

    async def broadcast_worker() -> None:
        while True:
            try:
                payload = await event_queue.get()
                dead: list[asyncio.Queue] = []
                for q in client_queues:
                    try:
                        q.put_nowait(payload)
                    except asyncio.QueueFull:
                        dead.append(q)
                for q in dead:
                    client_queues.discard(q)
            except asyncio.CancelledError:
                break
            except Exception:
                logger.exception("SSE broadcast worker error")

    broadcast_task = asyncio.create_task(broadcast_worker())
    app.state.sse_broadcast_task = broadcast_task
    app.state.sse_client_queues = client_queues
    app.state.sse_event_queue = event_queue

    def emit_device_event(device_id: str, reason: str, when: datetime) -> None:
        message = _device_event_message(reason)
        ts_iso = when.isoformat() if when.tzinfo else when.replace(tzinfo=timezone.utc).isoformat()
        payload = {
            "sse_event": "device_event",
            "device_id": device_id,
            "type": reason,
            "message": message,
            "timestamp": ts_iso,
        }
        try:
            loop.call_soon_threadsafe(event_queue.put_nowait, payload)
        except Exception:
            logger.debug("SSE queue put failed for device_event", exc_info=True)

    mqtt = MQTTService.from_env()
    mqtt.set_event_broadcast(loop, event_queue)
    mqtt.set_device_event_callback(emit_device_event)
    mqtt.start()
    app.state.mqtt = mqtt
    logger.info("MQTT service started")

    motor_monitor = MotorMonitor(mqtt, on_event_emitted=emit_device_event)
    mqtt.set_motor_monitor(motor_monitor)
    motor_monitor.start()
    app.state.motor_monitor = motor_monitor
    logger.info("MotorMonitor started")

    yield

    broadcast_task.cancel()
    try:
        await broadcast_task
    except asyncio.CancelledError:
        pass
    motor_monitor.stop()
    logger.info("MotorMonitor stopped")
    mqtt.stop()
    logger.info("MQTT service stopped")


app = FastAPI(
    title="SmartMotor Backend",
    description="Personal IoT backend using MQTT",
    lifespan=lifespan,
)

app.include_router(auth_router)
app.include_router(room_routes)
app.include_router(alexa_routes)

# ---------------------------------------------------------------------------
# CORS (safe for development; restrict in production if needed)
# ---------------------------------------------------------------------------

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # restrict later if needed
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Response models
# ---------------------------------------------------------------------------

class DeviceState(BaseModel):
    id: str
    online: bool
    last_seen: datetime | None = None
    last_state: str | None = None
    last_power: float | None = None


class DevicesResponse(BaseModel):
    devices: list[DeviceState]


class DeviceEventItem(BaseModel):
    timestamp: datetime
    type: str  # DRY_RUN | SAFETY_TIMER | TANK_FULL | OFFLINE | ONLINE (never raw DB value)
    message: str


class CommandRequest(BaseModel):
    action: str  # "ON" | "OFF" (simple validation below)


class CommandStatus(BaseModel):
    command_id: str
    device_id: str
    action: str
    status: str
    created_at: datetime
    updated_at: datetime


class SimulateStateRequest(BaseModel):
    state: str | None = None
    power: float | None = None


class SimulateAckRequest(BaseModel):
    command_id: str
# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def build_device_state(device_id: str, state: dict | None) -> DeviceState:
    if not state:
        return DeviceState(
            id=device_id,
            online=False,
            last_seen=None,
            last_state=None,
            last_power=None,
        )

    ts = state.get("last_seen_ts")
    last_seen = (
        datetime.fromtimestamp(ts, tz=timezone.utc) if ts else None
    )

    return DeviceState(
        id=device_id,
        online=state.get("online", False),
        last_seen=last_seen,
        last_state=state.get("last_state"),
        last_power=state.get("last_power"),
    )

# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

def _user_device_ids(db: Session, user_id: int) -> set[str]:
    rows = db.query(DeviceOwnership.device_id).filter(DeviceOwnership.user_id == user_id).all()
    return {r[0] for r in rows}


@app.get("/devices", response_model=DevicesResponse)
def get_devices(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    allowed = _user_device_ids(db, current_user.id)
    svc: MQTTService = app.state.mqtt
    snapshot = svc.snapshot_states()

    devices = [
        build_device_state(device_id, state)
        for device_id, state in snapshot.items()
        if device_id in allowed
    ]

    return DevicesResponse(devices=devices)


@app.post("/devices/claim")
def claim_device(
    body: ClaimRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    now = datetime.now(tz=timezone.utc)
    session = (
        db.query(DevicePairingSession)
        .filter(
            DevicePairingSession.pair_code == body.pair_code.strip(),
            DevicePairingSession.expires_at > now,
        )
        .first()
    )
    if not session:
        raise HTTPException(status_code=400, detail="Invalid or expired code")
    device_id = session.device_id
    device = db.get(Device, device_id)
    if not device:
        db.add(Device(id=device_id))
    existing = (
        db.query(DeviceOwnership)
        .filter(DeviceOwnership.user_id == current_user.id, DeviceOwnership.device_id == device_id)
        .first()
    )
    if existing:
        db.delete(session)
        db.commit()
        return {"status": "already_claimed", "device_id": device_id}
    session.claimed_by_user_id = current_user.id
    db.add(DeviceOwnership(user_id=current_user.id, device_id=device_id, role="owner"))
    db.delete(session)
    db.commit()
    return {"status": "claimed", "device_id": device_id}


@app.get("/devices/{device_id}", response_model=DeviceState)
def get_device(
    device_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    allowed = _user_device_ids(db, current_user.id)
    if device_id not in allowed:
        raise HTTPException(status_code=404, detail="Device not found")
    svc: MQTTService = app.state.mqtt
    snapshot = svc.snapshot_states()
    state = snapshot.get(device_id)
    return build_device_state(device_id, state)


@app.get("/devices/{device_id}/events", response_model=list[DeviceEventItem])
def get_device_events(
    device_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Last 50 device events (newest first). type + message only; never raw reason."""
    _require_device_ownership(db, current_user.id, device_id)
    rows = (
        db.query(DeviceEvent)
        .filter(DeviceEvent.device_id == device_id)
        .order_by(DeviceEvent.timestamp.desc())
        .limit(50)
        .all()
    )
    return [
        DeviceEventItem(
            timestamp=r.timestamp,
            type=r.reason,
            message=_device_event_message(r.reason),
        )
        for r in rows
    ]


@app.post("/devices/{device_id}/on")
def device_on(
    device_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    _require_device_ownership(db, current_user.id, device_id)
    
    # Track device ON activity
    activity = UserActivity(
        user_id=current_user.id,
        activity_type="DEVICE_ON",
        device_id=device_id,
        details=f"Turned ON device {device_id}"
    )
    db.add(activity)
    db.commit()
    
    return send_command(device_id, CommandRequest(action="ON"), current_user, db)


@app.post("/devices/{device_id}/off")
def device_off(
    device_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    _require_device_ownership(db, current_user.id, device_id)
    
    # Track device OFF activity
    activity = UserActivity(
        user_id=current_user.id,
        activity_type="DEVICE_OFF",
        device_id=device_id,
        details=f"Turned OFF device {device_id}"
    )
    db.add(activity)
    db.commit()
    
    return send_command(device_id, CommandRequest(action="OFF"), current_user, db)


@app.post("/devices/{device_id}/unlock")
def device_unlock(
    device_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    _require_device_ownership(db, current_user.id, device_id)
    app.state.mqtt.create_command(device_id, "UNLOCK")
    return {"status": "sent", "device_id": device_id, "action": "UNLOCK"}


@app.post("/devices/{device_id}/reprovision_wifi")
def reprovision_wifi(
    device_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    _require_device_ownership(db, current_user.id, device_id)
    app.state.mqtt.create_command(device_id, "REPROVISION_WIFI")
    return {"status": "sent", "device_id": device_id, "action": "REPROVISION_WIFI"}


def _require_device_ownership(db: Session, user_id: int, device_id: str) -> None:
    allowed = _user_device_ids(db, user_id)
    if device_id not in allowed:
        raise HTTPException(status_code=404, detail="Device not found")


@app.post("/devices/{device_id}/command", response_model=CommandStatus)
def send_command(
    device_id: str,
    req: CommandRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    _require_device_ownership(db, current_user.id, device_id)
    action = req.action.upper()
    if action not in {"ON", "OFF"}:
        raise HTTPException(status_code=400, detail="action must be 'ON' or 'OFF'")

    svc: MQTTService = app.state.mqtt
    cmd_id = svc.create_command(device_id, action)
    info = svc.get_command(cmd_id)
    return _build_command_status(cmd_id, info)


@app.get("/commands/{command_id}", response_model=CommandStatus)
def get_command_status(
    command_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    svc: MQTTService = app.state.mqtt
    info = svc.get_command(command_id)
    if not info:
        raise HTTPException(status_code=404, detail="command not found")
    device_id = info.get("device_id")
    if device_id:
        _require_device_ownership(db, current_user.id, device_id)
    return _build_command_status(command_id, info)


@app.get("/health")
def health():
    svc: MQTTService = app.state.mqtt
    return {
        "status": "ok",
        "mqtt_connected": svc.is_connected,
        "known_devices": list(svc.snapshot_states().keys()),
    }


async def _sse_stream(client_queue: asyncio.Queue, client_queues: set) -> AsyncGenerator[str, None]:
    try:
        while True:
            try:
                payload = await asyncio.wait_for(client_queue.get(), timeout=20.0)
            except asyncio.TimeoutError:
                yield ": heartbeat\n\n"
                continue
            # device_event uses sse_event for event name; data omits it
            if "sse_event" in payload:
                event_type = payload["sse_event"]
                data_payload = {k: v for k, v in payload.items() if k != "sse_event"}
            else:
                event_type = payload.get("type", "message")
                data_payload = payload
            data = json.dumps(data_payload, separators=(",", ":"))
            yield f"event: {event_type}\ndata: {data}\n\n"
    finally:
        client_queues.discard(client_queue)


@app.get("/events")
def stream_events(
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
):
    """
    Server-Sent Events: stream device_update whenever MQTT receives heartbeat/state/power/ack.
    Content-Type: text/event-stream. Kept open; heartbeat every 20s.
    """
    client_queues: set[asyncio.Queue] = request.app.state.sse_client_queues
    client_queue: asyncio.Queue = asyncio.Queue()
    client_queues.add(client_queue)
    return StreamingResponse(
        _sse_stream(client_queue, client_queues),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.post("/simulate/{device_id}/online")
def simulate_online(
    device_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
):
    """
    Test helper: mark a device as if a heartbeat MQTT message was received.
    """
    svc: MQTTService = app.state.mqtt
    svc.simulate_heartbeat(device_id)
    return {"status": "ok", "device_id": device_id}


@app.post("/simulate/{device_id}/state")
def simulate_state(
    device_id: str,
    body: SimulateStateRequest,
    current_user: Annotated[User, Depends(get_current_user)],
):
    """
    Test helper: update device state/power as if MQTT /state and /power messages were received.
    """
    svc: MQTTService = app.state.mqtt
    state_value = body.state
    if state_value is not None:
        state_value = state_value.upper()
    svc.simulate_state(device_id, state_value=state_value, power_value=body.power)
    return {
        "status": "ok",
        "device_id": device_id,
        "state": state_value,
        "power": body.power,
    }


@app.post("/simulate/{device_id}/ack")
def simulate_ack(
    device_id: str,
    body: SimulateAckRequest,
    current_user: Annotated[User, Depends(get_current_user)],
):
    """
    Test helper: simulate a command ACK as if /devices/{deviceId}/ack with the same command_id was received.
    """
    svc: MQTTService = app.state.mqtt
    svc.simulate_ack(body.command_id)
    return {"status": "ok", "device_id": device_id, "command_id": body.command_id}


def _build_command_status(command_id: str, info: dict | None) -> CommandStatus:
    if not info:
        # Should only be used when info exists; fallback just in case
        now = datetime.now(tz=timezone.utc)
        return CommandStatus(
            command_id=command_id,
            device_id="unknown",
            action="UNKNOWN",
            status="UNKNOWN",
            created_at=now,
            updated_at=now,
        )
    created_ts = info.get("created_at_ts", time.time())
    updated_ts = info.get("updated_at_ts", created_ts)
    return CommandStatus(
        command_id=command_id,
        device_id=info.get("device_id", "unknown"),
        action=info.get("action", "UNKNOWN"),
        status=info.get("status", "PENDING"),
        created_at=datetime.fromtimestamp(created_ts, tz=timezone.utc),
        updated_at=datetime.fromtimestamp(updated_ts, tz=timezone.utc),
    )
