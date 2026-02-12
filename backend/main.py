"""
FastAPI backend for personal IoT project.
No Tuya. Talks to private MQTT broker and ESP32 devices.
"""

from dotenv import load_dotenv
import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from mqtt_service import MQTTService
from db import init_db

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

    mqtt = MQTTService.from_env()
    mqtt.start()
    app.state.mqtt = mqtt
    logger.info("MQTT service started")

    yield

    mqtt.stop()
    logger.info("MQTT service stopped")


app = FastAPI(
    title="SmartMotor Backend",
    description="Personal IoT backend using MQTT",
    lifespan=lifespan,
)

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


class CommandRequest(BaseModel):
    action: str  # "ON" | "OFF" (simple validation below)


class CommandStatus(BaseModel):
    command_id: str
    device_id: str
    action: str
    status: str
    created_at: datetime
    updated_at: datetime
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

@app.get("/devices", response_model=DevicesResponse)
def get_devices():
    svc: MQTTService = app.state.mqtt
    snapshot = svc.snapshot_states()

    devices = [
        build_device_state(device_id, state)
        for device_id, state in snapshot.items()
    ]

    return DevicesResponse(devices=devices)


@app.get("/devices/{device_id}", response_model=DeviceState)
def get_device(device_id: str):
    svc: MQTTService = app.state.mqtt
    snapshot = svc.snapshot_states()
    state = snapshot.get(device_id)

    return build_device_state(device_id, state)


@app.post("/devices/{device_id}/on")
def device_on(device_id: str):
    # Backwards-compat shim: maps to unified /command
    return send_command(device_id, CommandRequest(action="ON"))


@app.post("/devices/{device_id}/off")
def device_off(device_id: str):
    # Backwards-compat shim
    return send_command(device_id, CommandRequest(action="OFF"))


@app.post("/devices/{device_id}/unlock")
def device_unlock(device_id: str):
    app.state.mqtt.create_command(device_id, "UNLOCK")
    return {"status": "sent", "device_id": device_id, "action": "UNLOCK"}


@app.post("/devices/{device_id}/reprovision_wifi")
def reprovision_wifi(device_id: str):
    app.state.mqtt.create_command(device_id, "REPROVISION_WIFI")
    return {"status": "sent", "device_id": device_id, "action": "REPROVISION_WIFI"}


@app.post("/devices/{device_id}/command", response_model=CommandStatus)
def send_command(device_id: str, req: CommandRequest):
    action = req.action.upper()
    if action not in {"ON", "OFF"}:
        raise HTTPException(status_code=400, detail="action must be 'ON' or 'OFF'")

    svc: MQTTService = app.state.mqtt
    cmd_id = svc.create_command(device_id, action)
    info = svc.get_command(cmd_id)
    return _build_command_status(cmd_id, info)


@app.get("/commands/{command_id}", response_model=CommandStatus)
def get_command_status(command_id: str):
    svc: MQTTService = app.state.mqtt
    info = svc.get_command(command_id)
    if not info:
        raise HTTPException(status_code=404, detail="command not found")
    return _build_command_status(command_id, info)


@app.get("/health")
def health():
    svc: MQTTService = app.state.mqtt
    return {
        "status": "ok",
        "mqtt_connected": svc.is_connected,
        "known_devices": list(svc.snapshot_states().keys()),
    }


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
