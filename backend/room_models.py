from datetime import datetime, timezone

from sqlalchemy import Column, String, DateTime, Integer, ForeignKey, UniqueConstraint, Boolean

from db import Base


def utcnow() -> datetime:
    return datetime.now(tz=timezone.utc)


class Room(Base):
    """Grouping of devices into rooms/areas."""
    __tablename__ = "rooms"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    name = Column(String, nullable=False)
    description = Column(String, nullable=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)


class RoomDevice(Base):
    """Association between rooms and devices."""
    __tablename__ = "room_devices"

    room_id = Column(Integer, ForeignKey("rooms.id", ondelete="CASCADE"), primary_key=True)
    device_id = Column(String, ForeignKey("devices.id", ondelete="CASCADE"), primary_key=True)
    __table_args__ = (UniqueConstraint("room_id", "device_id", name="uq_room_device"),)


class Schedule(Base):
    """Scheduled automation for devices or rooms."""
    __tablename__ = "schedules"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    name = Column(String, nullable=False)
    description = Column(String, nullable=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    room_id = Column(Integer, ForeignKey("rooms.id", ondelete="CASCADE"), nullable=True, index=True)
    device_id = Column(String, ForeignKey("devices.id", ondelete="CASCADE"), nullable=True, index=True)
    schedule_type = Column(String, nullable=False)  # "DAILY", "WEEKLY", "MONTHLY"
    time_of_day = Column(String, nullable=False)  # HH:MM format
    days_of_week = Column(String, nullable=True)  # Comma-separated days for WEEKLY
    action = Column(String, nullable=False)  # "ON", "OFF"
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    last_run = Column(DateTime(timezone=True), nullable=True)
    
    __table_args__ = (
        UniqueConstraint("room_id", "device_id", name="uq_schedule_room_device"),
    )