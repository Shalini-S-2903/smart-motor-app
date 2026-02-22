import asyncio
import logging
import time
from datetime import datetime, timezone, timedelta
from typing import List

from sqlalchemy.orm import Session
from db import SessionLocal
from room_models import Schedule, RoomDevice
from models import DeviceOwnership

logger = logging.getLogger("scheduler-service")

class SchedulerService:
    def __init__(self, mqtt_service):
        self.mqtt_service = mqtt_service
        self._stop_event = asyncio.Event()
        self._task = None

    def start(self):
        if self._task is None:
            self._task = asyncio.create_task(self._run())
            logger.info("Scheduler service started")

    async def stop(self):
        if self._task:
            self._stop_event.set()
            await self._task
            self._task = None
            logger.info("Scheduler service stopped")

    async def _run(self):
        while not self._stop_event.is_set():
            try:
                await self._check_and_execute_schedules()
            except Exception:
                logger.exception("Error in scheduler loop")
            
            # Check every 1 second for due tasks. 
            # For "microsecond" precision (hyperbolic), we can't do much over network, 
            # but we can reduce loop time to 0.1 if needed. 1s is standard for Smart Home.
            await asyncio.sleep(1.0)

    async def _check_and_execute_schedules(self):
        with SessionLocal() as db:
            now = datetime.now(timezone.utc)
            # Find active schedules that haven't run in the last minute and are due
            # We filter by is_active and (for ONEOFF) not is_executed
            schedules = db.query(Schedule).filter(
                Schedule.is_active == True,
                (Schedule.is_executed == False) | (Schedule.schedule_type != "ONEOFF")
            ).all()

            for s in schedules:
                if self._is_due(s, now):
                    await self._execute(s, db)

    def _is_due(self, schedule: Schedule, now: datetime) -> bool:
        # Check if already run in this specific minute to avoid double triggers
        if schedule.last_run:
            if schedule.last_run.replace(second=0, microsecond=0) == now.replace(second=0, microsecond=0):
                return False

        # Parse schedule time
        try:
            h, m, s = map(int, schedule.time_of_day.split(":"))
        except:
            return False

        if schedule.schedule_type == "ONEOFF":
            if not schedule.scheduled_date:
                return False
            try:
                sched_dt = datetime.fromisoformat(f"{schedule.scheduled_date}T{schedule.time_of_day}").replace(tzinfo=timezone.utc)
                # If now is past scheduled time (within a small window or exact)
                return now >= sched_dt and now < sched_dt + timedelta(minutes=5)
            except:
                return False

        elif schedule.schedule_type == "DAILY":
            # Compare time part
            return now.hour == h and now.minute == m and now.second >= s

        elif schedule.schedule_type == "WEEKLY":
            if not schedule.days_of_week:
                return False
            # Day of week from now (MON, TUE, etc.)
            now_day = now.strftime("%a").upper() # "MON"
            if now_day in schedule.days_of_week.upper():
                return now.hour == h and now.minute == m and now.second >= s
        
        return False

    async def _execute(self, schedule: Schedule, db: Session):
        action = schedule.action.upper()
        logger.info(f"Executing schedule {schedule.id} ({schedule.name}): {action}")

        target_device_ids = []
        if schedule.device_id:
            target_device_ids.append(schedule.device_id)
        elif schedule.room_id:
            room_devices = db.query(RoomDevice).filter(RoomDevice.room_id == schedule.room_id).all()
            target_device_ids = [rd.device_id for rd in room_devices]

        if not target_device_ids:
            logger.warning(f"Schedule {schedule.id} has no target devices")
        else:
            for device_id in target_device_ids:
                # Check if user still owns it (safety check)
                owner = db.query(DeviceOwnership).filter(
                    DeviceOwnership.user_id == schedule.user_id,
                    DeviceOwnership.device_id == device_id
                ).first()
                if owner:
                    self.mqtt_service.create_command(device_id, action)
                    logger.info(f"Sent {action} to {device_id} for schedule {schedule.id}")

        # Update schedule status
        schedule.last_run = datetime.now(timezone.utc)
        if schedule.schedule_type == "ONEOFF":
            schedule.is_executed = True
        
        db.commit()
