from datetime import datetime, timezone
from typing import Annotated, List

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from auth import get_current_user
from db import get_db
from models import User, Device, DeviceOwnership
from room_models import Room, RoomDevice, Schedule

router = APIRouter(prefix="/rooms", tags=["rooms"])


# ---------------------------------------------------------------------------
# Pydantic schemas
# ---------------------------------------------------------------------------

class RoomCreate(BaseModel):
    name: str
    description: str | None = None


class RoomResponse(BaseModel):
    id: int
    name: str
    description: str | None
    created_at: datetime
    is_active: bool
    device_count: int


class RoomDeviceAdd(BaseModel):
    device_id: str


class ScheduleCreate(BaseModel):
    name: str
    description: str | None = None
    room_id: int | None = None
    device_id: str | None = None
    schedule_type: str  # DAILY, WEEKLY, MONTHLY
    time_of_day: str  # HH:MM format
    days_of_week: str | None = None  # For WEEKLY: "MON,TUE,WED"
    action: str  # ON, OFF


class ScheduleResponse(BaseModel):
    id: int
    name: str
    description: str | None
    room_id: int | None
    device_id: str | None
    schedule_type: str
    time_of_day: str
    days_of_week: str | None
    action: str
    is_active: bool
    created_at: datetime
    last_run: datetime | None


# ---------------------------------------------------------------------------
# Room endpoints
# ---------------------------------------------------------------------------

@router.get("/", response_model=List[RoomResponse])
def get_rooms(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Get all rooms for the current user."""
    rooms = db.query(Room).filter(Room.user_id == current_user.id, Room.is_active == True).all()
    
    result = []
    for room in rooms:
        device_count = db.query(RoomDevice).filter(RoomDevice.room_id == room.id).count()
        result.append(RoomResponse(
            id=room.id,
            name=room.name,
            description=room.description,
            created_at=room.created_at,
            is_active=room.is_active,
            device_count=device_count
        ))
    
    return result


@router.post("/", response_model=RoomResponse)
def create_room(
    room_data: RoomCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Create a new room."""
    room = Room(
        name=room_data.name,
        description=room_data.description,
        user_id=current_user.id
    )
    db.add(room)
    db.commit()
    db.refresh(room)
    
    return RoomResponse(
        id=room.id,
        name=room.name,
        description=room.description,
        created_at=room.created_at,
        is_active=room.is_active,
        device_count=0
    )


@router.get("/{room_id}", response_model=RoomResponse)
def get_room(
    room_id: int,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Get a specific room."""
    room = db.query(Room).filter(Room.id == room_id, Room.user_id == current_user.id).first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    
    device_count = db.query(RoomDevice).filter(RoomDevice.room_id == room.id).count()
    
    return RoomResponse(
        id=room.id,
        name=room.name,
        description=room.description,
        created_at=room.created_at,
        is_active=room.is_active,
        device_count=device_count
    )


@router.delete("/{room_id}")
def delete_room(
    room_id: int,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Delete a room."""
    room = db.query(Room).filter(Room.id == room_id, Room.user_id == current_user.id).first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    
    # Delete associated room devices
    db.query(RoomDevice).filter(RoomDevice.room_id == room_id).delete()
    
    # Delete associated schedules
    db.query(Schedule).filter(Schedule.room_id == room_id).delete()
    
    db.delete(room)
    db.commit()
    
    return {"message": "Room deleted successfully"}


# ---------------------------------------------------------------------------
# Room device endpoints
# ---------------------------------------------------------------------------

@router.get("/{room_id}/devices")
def get_room_devices(
    room_id: int,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Get all devices in a room."""
    room = db.query(Room).filter(Room.id == room_id, Room.user_id == current_user.id).first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    
    room_devices = db.query(RoomDevice).filter(RoomDevice.room_id == room_id).all()
    device_ids = [rd.device_id for rd in room_devices]
    
    devices = db.query(Device).filter(Device.id.in_(device_ids)).all()
    
    return [{"id": d.id, "first_seen": d.first_seen} for d in devices]


@router.post("/{room_id}/devices")
def add_device_to_room(
    room_id: int,
    device_data: RoomDeviceAdd,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Add a device to a room."""
    room = db.query(Room).filter(Room.id == room_id, Room.user_id == current_user.id).first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    
    # Check if user owns the device
    ownership = db.query(DeviceOwnership).filter(
        DeviceOwnership.user_id == current_user.id,
        DeviceOwnership.device_id == device_data.device_id
    ).first()
    if not ownership:
        raise HTTPException(status_code=404, detail="Device not found or not owned")
    
    # Check if device is already in room
    existing = db.query(RoomDevice).filter(
        RoomDevice.room_id == room_id,
        RoomDevice.device_id == device_data.device_id
    ).first()
    
    if existing:
        raise HTTPException(status_code=400, detail="Device already in room")
    
    room_device = RoomDevice(room_id=room_id, device_id=device_data.device_id)
    db.add(room_device)
    db.commit()
    
    return {"message": "Device added to room successfully"}


@router.delete("/{room_id}/devices/{device_id}")
def remove_device_from_room(
    room_id: int,
    device_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Remove a device from a room."""
    room = db.query(Room).filter(Room.id == room_id, Room.user_id == current_user.id).first()
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    
    room_device = db.query(RoomDevice).filter(
        RoomDevice.room_id == room_id,
        RoomDevice.device_id == device_id
    ).first()
    
    if not room_device:
        raise HTTPException(status_code=404, detail="Device not found in room")
    
    db.delete(room_device)
    db.commit()
    
    return {"message": "Device removed from room successfully"}


# ---------------------------------------------------------------------------
# Schedule endpoints
# ---------------------------------------------------------------------------

@router.get("/schedules/", response_model=List[ScheduleResponse])
def get_schedules(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Get all schedules for the current user."""
    schedules = db.query(Schedule).filter(Schedule.user_id == current_user.id).all()
    
    return [
        ScheduleResponse(
            id=s.id,
            name=s.name,
            description=s.description,
            room_id=s.room_id,
            device_id=s.device_id,
            schedule_type=s.schedule_type,
            time_of_day=s.time_of_day,
            days_of_week=s.days_of_week,
            action=s.action,
            is_active=s.is_active,
            created_at=s.created_at,
            last_run=s.last_run
        )
        for s in schedules
    ]


@router.post("/schedules/", response_model=ScheduleResponse)
def create_schedule(
    schedule_data: ScheduleCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Create a new schedule."""
    # Validate room/device ownership
    if schedule_data.room_id:
        room = db.query(Room).filter(Room.id == schedule_data.room_id, Room.user_id == current_user.id).first()
        if not room:
            raise HTTPException(status_code=404, detail="Room not found or not owned")
    
    if schedule_data.device_id:
        ownership = db.query(DeviceOwnership).filter(
            DeviceOwnership.user_id == current_user.id,
            DeviceOwnership.device_id == schedule_data.device_id
        ).first()
        if not ownership:
            raise HTTPException(status_code=404, detail="Device not found or not owned")
    
    # Validate schedule type and time format
    if schedule_data.schedule_type not in ["DAILY", "WEEKLY", "MONTHLY"]:
        raise HTTPException(status_code=400, detail="Invalid schedule type")
    
    if schedule_data.action not in ["ON", "OFF"]:
        raise HTTPException(status_code=400, detail="Invalid action")
    
    # Basic time format validation (HH:MM)
    try:
        time_parts = schedule_data.time_of_day.split(":")
        if len(time_parts) != 2:
            raise ValueError()
        hour, minute = int(time_parts[0]), int(time_parts[1])
        if not (0 <= hour <= 23 and 0 <= minute <= 59):
            raise ValueError()
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid time format. Use HH:MM")
    
    schedule = Schedule(
        name=schedule_data.name,
        description=schedule_data.description,
        user_id=current_user.id,
        room_id=schedule_data.room_id,
        device_id=schedule_data.device_id,
        schedule_type=schedule_data.schedule_type,
        time_of_day=schedule_data.time_of_day,
        days_of_week=schedule_data.days_of_week,
        action=schedule_data.action
    )
    
    db.add(schedule)
    db.commit()
    db.refresh(schedule)
    
    return ScheduleResponse(
        id=schedule.id,
        name=schedule.name,
        description=schedule.description,
        room_id=schedule.room_id,
        device_id=schedule.device_id,
        schedule_type=schedule.schedule_type,
        time_of_day=schedule.time_of_day,
        days_of_week=schedule.days_of_week,
        action=schedule.action,
        is_active=schedule.is_active,
        created_at=schedule.created_at,
        last_run=schedule.last_run
    )


@router.put("/schedules/{schedule_id}")
def update_schedule(
    schedule_id: int,
    schedule_data: ScheduleCreate,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Update a schedule."""
    schedule = db.query(Schedule).filter(Schedule.id == schedule_id, Schedule.user_id == current_user.id).first()
    if not schedule:
        raise HTTPException(status_code=404, detail="Schedule not found")
    
    # Validate room/device ownership
    if schedule_data.room_id:
        room = db.query(Room).filter(Room.id == schedule_data.room_id, Room.user_id == current_user.id).first()
        if not room:
            raise HTTPException(status_code=404, detail="Room not found or not owned")
    
    if schedule_data.device_id:
        ownership = db.query(DeviceOwnership).filter(
            DeviceOwnership.user_id == current_user.id,
            DeviceOwnership.device_id == schedule_data.device_id
        ).first()
        if not ownership:
            raise HTTPException(status_code=404, detail="Device not found or not owned")
    
    # Update fields
    schedule.name = schedule_data.name
    schedule.description = schedule_data.description
    schedule.room_id = schedule_data.room_id
    schedule.device_id = schedule_data.device_id
    schedule.schedule_type = schedule_data.schedule_type
    schedule.time_of_day = schedule_data.time_of_day
    schedule.days_of_week = schedule_data.days_of_week
    schedule.action = schedule_data.action
    
    db.commit()
    
    return {"message": "Schedule updated successfully"}


@router.delete("/schedules/{schedule_id}")
def delete_schedule(
    schedule_id: int,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
):
    """Delete a schedule."""
    schedule = db.query(Schedule).filter(Schedule.id == schedule_id, Schedule.user_id == current_user.id).first()
    if not schedule:
        raise HTTPException(status_code=404, detail="Schedule not found")
    
    db.delete(schedule)
    db.commit()
    
    return {"message": "Schedule deleted successfully"}