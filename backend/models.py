from datetime import datetime, timezone

from sqlalchemy import Column, String, DateTime, Integer, Float, ForeignKey

from db import Base


def utcnow() -> datetime:
    return datetime.now(tz=timezone.utc)


class Device(Base):
    __tablename__ = "devices"

    id = Column(String, primary_key=True, index=True)
    first_seen = Column(DateTime(timezone=True), default=utcnow, nullable=False)


class DeviceStateHistory(Base):
    __tablename__ = "device_state_history"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    device_id = Column(String, ForeignKey("devices.id"), index=True, nullable=False)
    timestamp = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    state = Column(String, nullable=True)
    power = Column(Float, nullable=True)


class Command(Base):
    __tablename__ = "commands"

    command_id = Column(String, primary_key=True, index=True)
    device_id = Column(String, index=True, nullable=False)
    action = Column(String, nullable=False)
    status = Column(String, nullable=False)  # PENDING | SUCCESS | FAILED
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    completed_at = Column(DateTime(timezone=True), nullable=True)

