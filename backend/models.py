from datetime import datetime, timezone

from sqlalchemy import Column, String, DateTime, Integer, Float, ForeignKey, UniqueConstraint

from db import Base


def utcnow() -> datetime:
    return datetime.now(tz=timezone.utc)


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    email = Column(String, unique=True, index=True, nullable=False)
    password_hash = Column(String, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow, nullable=False)


class Device(Base):
    __tablename__ = "devices"

    id = Column(String, primary_key=True, index=True)
    first_seen = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    fill_mode = Column(String, nullable=True)  # "1" / true-like for SMART_FILL
    max_runtime_minutes = Column(Integer, nullable=True)  # default 20 in code


class DeviceEvent(Base):
    """Safety / monitoring events: DRY_RUN, SAFETY_TIMER, TANK_FULL."""
    __tablename__ = "device_events"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    device_id = Column(String, ForeignKey("devices.id"), nullable=False, index=True)
    timestamp = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    reason = Column(String, nullable=False)


class DevicePairingSession(Base):
    """Temporary pairing: device publishes code to /devices/{id}/pairing; 5 min expiry."""
    __tablename__ = "device_pairing_sessions"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    device_id = Column(String, ForeignKey("devices.id"), nullable=False, index=True, unique=True)
    pair_code = Column(String, nullable=False, index=True)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    claimed_by_user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)


class DeviceOwnership(Base):
    __tablename__ = "device_ownership"

    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    device_id = Column(String, ForeignKey("devices.id", ondelete="CASCADE"), primary_key=True)
    role = Column(String, nullable=False, default="owner")  # e.g. owner, viewer
    __table_args__ = (UniqueConstraint("user_id", "device_id", name="uq_device_ownership"),)


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


class UserActivity(Base):
    """Track user login activity and device interactions."""
    __tablename__ = "user_activities"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    activity_type = Column(String, nullable=False)  # LOGIN, LOGOUT, DEVICE_ON, DEVICE_OFF, etc.
    device_id = Column(String, ForeignKey("devices.id"), nullable=True, index=True)
    timestamp = Column(DateTime(timezone=True), default=utcnow, nullable=False)
    details = Column(String, nullable=True)  # Optional JSON or free-form details

