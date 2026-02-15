from datetime import datetime, timezone
from typing import Annotated, List, Dict, Any

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from auth import get_current_user
from db import get_db
from models import User, Device, DeviceOwnership
from room_models import Room, RoomDevice, Schedule

router = APIRouter(prefix="/alexa", tags=["alexa"])


# ---------------------------------------------------------------------------
# Pydantic schemas for Alexa integration
# ---------------------------------------------------------------------------

class AlexaRequest(BaseModel):
    version: str
    session: Dict[str, Any]
    context: Dict[str, Any]
    request: Dict[str, Any]


class AlexaResponse(BaseModel):
    version: str
    response: Dict[str, Any]
    sessionAttributes: Dict[str, Any] = {}


class AlexaDiscoveryResponse(BaseModel):
    event: Dict[str, Any]
    context: Dict[str, Any]


# ---------------------------------------------------------------------------
# Alexa Smart Home API endpoints
# ---------------------------------------------------------------------------

@router.post("/smart-home")
def alexa_smart_home(
    request: AlexaRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Handle Alexa Smart Home API requests."""
    request_type = request.request.get("type")
    
    if request_type == "Alexa.Discovery.Discover":
        return handle_discovery(request, current_user, db)
    elif request_type == "Alexa.PowerController":
        return handle_power_control(request, current_user, db)
    else:
        raise HTTPException(status_code=400, detail=f"Unsupported request type: {request_type}")


def handle_discovery(
    request: AlexaRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Handle Alexa device discovery request."""
    # Get all devices owned by the user
    device_ownerships = db.query(DeviceOwnership).filter(
        DeviceOwnership.user_id == current_user.id
    ).all()
    
    device_ids = [ownership.device_id for ownership in device_ownerships]
    devices = db.query(Device).filter(Device.id.in_(device_ids)).all()
    
    # Build discovery response
    endpoints = []
    for device in devices:
        endpoint = {
            "endpointId": device.id,
            "manufacturerName": "SmartMotor",
            "friendlyName": f"Motor {device.id}",
            "description": "Smart Motor Controller",
            "displayCategories": ["SWITCH"],
            "cookie": {},
            "capabilities": [
                {
                    "type": "AlexaInterface",
                    "interface": "Alexa.PowerController",
                    "version": "3",
                    "properties": {
                        "supported": [
                            {"name": "powerState"}
                        ],
                        "proactivelyReported": True,
                        "retrievable": True
                    }
                },
                {
                    "type": "AlexaInterface",
                    "interface": "Alexa.EndpointHealth",
                    "version": "3.2",
                    "properties": {
                        "supported": [
                            {"name": "connectivity"}
                        ],
                        "proactivelyReported": True,
                        "retrievable": True
                    }
                }
            ]
        }
        endpoints.append(endpoint)
    
    response = {
        "event": {
            "header": {
                "namespace": "Alexa.Discovery",
                "name": "Discover.Response",
                "payloadVersion": "3",
                "messageId": request.request.get("messageId", "unique-message-id")
            },
            "payload": {
                "endpoints": endpoints
            }
        }
    }
    
    return response


def handle_power_control(
    request: AlexaRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Handle Alexa power control requests (TurnOn/TurnOff)."""
    endpoint_id = request.request["endpoint"]["endpointId"]
    directive = request.request["directive"]
    name = directive["name"]  # TurnOn or TurnOff
    
    # Verify user owns this device
    ownership = db.query(DeviceOwnership).filter(
        DeviceOwnership.user_id == current_user.id,
        DeviceOwnership.device_id == endpoint_id
    ).first()
    
    if not ownership:
        raise HTTPException(status_code=404, detail="Device not found or not owned")
    
    # Get the main FastAPI app to access MQTT service
    from main import app
    
    # Send command to device
    action = "ON" if name == "TurnOn" else "OFF"
    mqtt_service = app.state.mqtt
    cmd_id = mqtt_service.create_command(endpoint_id, action)
    
    # Build response
    response = {
        "event": {
            "header": {
                "namespace": "Alexa",
                "name": "Response",
                "payloadVersion": "3",
                "messageId": request.request.get("messageId", "unique-message-id"),
                "correlationToken": request.request.get("correlationToken", "")
            },
            "endpoint": {
                "endpointId": endpoint_id
            },
            "payload": {}
        },
        "context": {
            "properties": [
                {
                    "namespace": "Alexa.PowerController",
                    "name": "powerState",
                    "value": "ON" if action == "ON" else "OFF",
                    "timeOfSample": datetime.now(timezone.utc).isoformat(),
                    "uncertaintyInMilliseconds": 500
                }
            ]
        }
    }
    
    return response


# ---------------------------------------------------------------------------
# Additional Alexa utility endpoints
# ---------------------------------------------------------------------------

@router.get("/devices")
def get_alexa_devices(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Get devices formatted for Alexa integration."""
    device_ownerships = db.query(DeviceOwnership).filter(
        DeviceOwnership.user_id == current_user.id
    ).all()
    
    device_ids = [ownership.device_id for ownership in device_ownerships]
    devices = db.query(Device).filter(Device.id.in_(device_ids)).all()
    
    return {
        "devices": [
            {
                "id": device.id,
                "name": f"Motor {device.id}",
                "online": True,  # This would be determined by actual device status
                "powerState": "OFF"  # This would be determined by actual device status
            }
            for device in devices
        ]
    }


@router.post("/simulate/{device_id}/alexa")
def simulate_alexa_command(
    device_id: str,
    command: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Simulate an Alexa command for testing purposes."""
    # Verify user owns this device
    ownership = db.query(DeviceOwnership).filter(
        DeviceOwnership.user_id == current_user.id,
        DeviceOwnership.device_id == device_id
    ).first()
    
    if not ownership:
        raise HTTPException(status_code=404, detail="Device not found or not owned")
    
    if command.upper() not in ["ON", "OFF"]:
        raise HTTPException(status_code=400, detail="Invalid command. Use ON or OFF")
    
    # Get the main FastAPI app to access MQTT service
    from main import app
    mqtt_service = app.state.mqtt
    cmd_id = mqtt_service.create_command(device_id, command.upper())
    
    return {
        "message": f"Alexa command '{command.upper()}' sent to device {device_id}",
        "command_id": cmd_id
    }