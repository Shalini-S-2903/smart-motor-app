from datetime import datetime, timezone
import uuid
import logging
from typing import Annotated, Dict, Any

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel
from sqlalchemy.orm import Session

from auth import decode_token
from db import get_db
from models import User, Device, DeviceOwnership
from mqtt_service import MQTTService

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/alexa", tags=["alexa"])


# ---------------------------------------------------------------------------
# Dependency: get user from Alexa JSON payload (body.directive.endpoint.scope.token or body.directive.payload.scope.token)
# ---------------------------------------------------------------------------

async def get_alexa_user(request: Request, db: Session = Depends(get_db)) -> User:
    try:
        body = await request.json()
        directive = body.get("directive", {})
        
        # Alexa tokens can be in payload.scope (Discovery) or endpoint.scope (Control)
        scope = directive.get("payload", {}).get("scope")
        if not scope:
            scope = directive.get("endpoint", {}).get("scope")
            
        if not scope or scope.get("type") != "BearerToken":
            raise HTTPException(status_code=401, detail="Missing or invalid scope")
            
        token = scope.get("token")
        if not token:
            raise HTTPException(status_code=401, detail="Missing token")
            
        payload = decode_token(token)
        if not payload or payload.get("type") != "access":
            raise HTTPException(status_code=401, detail="Invalid token")
            
        user_id = int(payload["sub"])
        user = db.get(User, user_id)
        if not user:
            raise HTTPException(status_code=401, detail="User not found")
        return user
    except Exception as e:
        logger.error(f"Alexa auth failed: {e}")
        raise HTTPException(status_code=401, detail="Authentication failed")


# ---------------------------------------------------------------------------
# Alexa Smart Home API endpoints
# ---------------------------------------------------------------------------

@router.post("/smart-home")
async def alexa_smart_home(
    request: Request,
    db: Annotated[Session, Depends(get_db)],
):
    """Entry point for Alexa Smart Home skill directives."""
    body = await request.json()
    directive = body.get("directive", {})
    header = directive.get("header", {})
    namespace = header.get("namespace")
    name = header.get("name")
    
    # Authenticate user from body
    user = await get_alexa_user(request, db)
    
    if namespace == "Alexa.Discovery" and name == "Discover":
        return handle_discovery(header, user, db)
    elif namespace == "Alexa.PowerController":
        return handle_power_control(directive, user, db)
    elif namespace == "Alexa" and name == "ReportState":
        return handle_report_state(directive, user, db)
    else:
        # Generic response for unsupported namespaces
        return create_alexa_error_response(header, "INVALID_DIRECTIVE")


def handle_discovery(header: Dict[str, Any], user: User, db: Session):
    """Report all devices owned by the user to Alexa."""
    ownerships = db.query(DeviceOwnership).filter(DeviceOwnership.user_id == user.id).all()
    device_ids = [o.device_id for o in ownerships]
    devices = db.query(Device).filter(Device.id.in_(device_ids)).all()
    
    endpoints = []
    for dev in devices:
        endpoints.append({
            "endpointId": dev.id,
            "manufacturerName": "SmartMotor",
            "friendlyName": f"Motor {dev.id}",
            "description": "Smart Motor Controller",
            "displayCategories": ["SWITCH"],
            "capabilities": [
                {
                    "type": "AlexaInterface",
                    "interface": "Alexa.PowerController",
                    "version": "3",
                    "properties": {
                        "supported": [{"name": "powerState"}],
                        "proactivelyReported": True,
                        "retrievable": True
                    }
                },
                {
                    "type": "AlexaInterface",
                    "interface": "Alexa.EndpointHealth",
                    "version": "3",
                    "properties": {
                        "supported": [{"name": "connectivity"}],
                        "proactivelyReported": True,
                        "retrievable": True
                    }
                },
                {
                    "type": "AlexaInterface",
                    "interface": "Alexa",
                    "version": "3"
                }
            ]
        })
        
    return {
        "event": {
            "header": {
                "namespace": "Alexa.Discovery",
                "name": "Discover.Response",
                "payloadVersion": "3",
                "messageId": str(uuid.uuid4())
            },
            "payload": {
                "endpoints": endpoints
            }
        }
    }


def handle_power_control(directive: Dict[str, Any], user: User, db: Session):
    """Handle TurnOn/TurnOff commands."""
    header = directive["header"]
    endpoint_id = directive["endpoint"]["endpointId"]
    action = "ON" if header["name"] == "TurnOn" else "OFF"
    
    # Verify ownership
    ownership = db.query(DeviceOwnership).filter(
        DeviceOwnership.user_id == user.id,
        DeviceOwnership.device_id == endpoint_id
    ).first()
    
    if not ownership:
        return create_alexa_error_response(header, "NO_SUCH_ENDPOINT")
        
    # Send MQTT command
    from main import app
    mqtt: MQTTService = app.state.mqtt
    mqtt.create_command(endpoint_id, action)
    
    return create_alexa_response(directive, "Alexa.PowerController", "powerState", action)


def handle_report_state(directive: Dict[str, Any], user: User, db: Session):
    """Report current state of the device."""
    endpoint_id = directive["endpoint"]["endpointId"]
    
    # Verify ownership
    ownership = db.query(DeviceOwnership).filter(
        DeviceOwnership.user_id == user.id,
        DeviceOwnership.device_id == endpoint_id
    ).first()
    
    if not ownership:
        return create_alexa_error_response(directive["header"], "NO_SUCH_ENDPOINT")
        
    from main import app
    mqtt: MQTTService = app.state.mqtt
    states = mqtt.snapshot_states()
    dev_state = states.get(endpoint_id, {})
    
    power_state = dev_state.get("last_state", "OFF")
    
    return {
        "event": {
            "header": {
                "namespace": "Alexa",
                "name": "StateReport",
                "payloadVersion": "3",
                "messageId": str(uuid.uuid4()),
                "correlationToken": directive["header"].get("correlationToken")
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
                    "value": power_state,
                    "timeOfSample": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                    "uncertaintyInMilliseconds": 0
                },
                {
                    "namespace": "Alexa.EndpointHealth",
                    "name": "connectivity",
                    "value": {"value": "OK" if dev_state.get("online") else "UNREACHABLE"},
                    "timeOfSample": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                    "uncertaintyInMilliseconds": 0
                }
            ]
        }
    }


def create_alexa_response(directive: Dict[str, Any], namespace: str, name: str, value: Any):
    return {
        "event": {
            "header": {
                "namespace": "Alexa",
                "name": "Response",
                "payloadVersion": "3",
                "messageId": str(uuid.uuid4()),
                "correlationToken": directive["header"].get("correlationToken")
            },
            "endpoint": {
                "endpointId": directive["endpoint"]["endpointId"]
            },
            "payload": {}
        },
        "context": {
            "properties": [
                {
                    "namespace": namespace,
                    "name": name,
                    "value": value,
                    "timeOfSample": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                    "uncertaintyInMilliseconds": 0
                }
            ]
        }
    }


def create_alexa_error_response(header: Dict[str, Any], error_type: str):
    return {
        "event": {
            "header": {
                "namespace": "Alexa",
                "name": "ErrorResponse",
                "payloadVersion": "3",
                "messageId": str(uuid.uuid4()),
                "correlationToken": header.get("correlationToken")
            },
            "payload": {
                "type": error_type,
                "message": "Error occurred during Alexa directive execution"
            }
        }
    }