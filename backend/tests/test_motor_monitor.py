"""
Unit tests for MotorMonitor.
Tests safety rules: DRY_RUN, SAFETY_TIMER, TANK_FULL.
"""
import time
import unittest
from unittest.mock import Mock, patch, MagicMock
from collections import deque

from motor_monitor import MotorMonitor, DRY_RUN_AVG_W_THRESHOLD, DEFAULT_MAX_RUNTIME_MINUTES


class TestMotorMonitor(unittest.TestCase):
    def setUp(self):
        """Set up test motor monitor."""
        self.mqtt_service = Mock()
        self.mqtt_service.create_command = Mock()
        self.monitor = MotorMonitor(self.mqtt_service, on_event_emitted=None)

    def test_eval_dry_run_triggered(self):
        """Test DRY_RUN detection when power < 40W for 15s."""
        device_id = "test-device-1"
        now = time.time()
        
        # Create power samples with low power (< 40W)
        power_samples = [
            (now - 10, 30.0),  # 10s ago
            (now - 5, 35.0),   # 5s ago
            (now - 2, 38.0),   # 2s ago
        ]
        
        reason = self.monitor._eval_dry_run(now, power_samples)
        self.assertEqual(reason, "DRY_RUN")

    def test_eval_dry_run_not_triggered_high_power(self):
        """Test DRY_RUN not triggered when power > 40W."""
        device_id = "test-device-1"
        now = time.time()
        
        # Create power samples with high power (> 40W)
        power_samples = [
            (now - 10, 100.0),
            (now - 5, 150.0),
            (now - 2, 120.0),
        ]
        
        reason = self.monitor._eval_dry_run(now, power_samples)
        self.assertIsNone(reason)

    def test_eval_dry_run_not_enough_samples(self):
        """Test DRY_RUN not triggered with insufficient samples."""
        now = time.time()
        power_samples = [(now - 5, 30.0)]  # Only 1 sample
        
        reason = self.monitor._eval_dry_run(now, power_samples)
        self.assertIsNone(reason)

    def test_eval_max_runtime_triggered(self):
        """Test SAFETY_TIMER detection when runtime > 20 minutes."""
        device_id = "test-device-1"
        now = time.time()
        running_since = now - (DEFAULT_MAX_RUNTIME_MINUTES * 60 + 60)  # 21 minutes ago
        
        with patch.object(self.monitor, '_get_max_runtime_minutes', return_value=DEFAULT_MAX_RUNTIME_MINUTES):
            reason = self.monitor._eval_max_runtime(device_id, now, running_since)
            self.assertEqual(reason, "SAFETY_TIMER")

    def test_eval_max_runtime_not_triggered(self):
        """Test SAFETY_TIMER not triggered when runtime < 20 minutes."""
        device_id = "test-device-1"
        now = time.time()
        running_since = now - (10 * 60)  # 10 minutes ago
        
        with patch.object(self.monitor, '_get_max_runtime_minutes', return_value=DEFAULT_MAX_RUNTIME_MINUTES):
            reason = self.monitor._eval_max_runtime(device_id, now, running_since)
            self.assertIsNone(reason)

    def test_eval_smart_fill_triggered(self):
        """Test TANK_FULL detection when power drops significantly."""
        device_id = "test-device-1"
        now = time.time()
        running_since = now - 20  # Running for 20 seconds
        
        # Stable power (100W) then drops to 30W (< 40% of stable)
        power_samples = [
            (now - 20, 100.0),  # Stable period
            (now - 15, 105.0),
            (now - 10, 98.0),
            (now - 5, 30.0),    # Recent drop
            (now - 2, 25.0),    # Recent drop
        ]
        
        with patch.object(self.monitor, '_is_fill_mode', return_value=True):
            reason = self.monitor._eval_smart_fill(device_id, now, running_since, power_samples)
            self.assertEqual(reason, "TANK_FULL")

    def test_eval_smart_fill_not_triggered_not_fill_mode(self):
        """Test TANK_FULL not triggered when not in fill mode."""
        device_id = "test-device-1"
        now = time.time()
        running_since = now - 20
        
        power_samples = [(now - 10, 100.0), (now - 5, 30.0)]
        
        with patch.object(self.monitor, '_is_fill_mode', return_value=False):
            reason = self.monitor._eval_smart_fill(device_id, now, running_since, power_samples)
            self.assertIsNone(reason)

    def test_on_state_power_updates_state(self):
        """Test on_state_power updates device state."""
        device_id = "test-device-1"
        now = time.time()
        
        # Set state to ON
        self.monitor.on_state_power(device_id, "ON", 150.0, now)
        
        with self.monitor._lock:
            device_data = self.monitor._devices.get(device_id)
            self.assertIsNotNone(device_data)
            self.assertEqual(device_data["last_state"], "ON")
            self.assertIsNotNone(device_data["running_since"])

    def test_on_state_power_updates_power_samples(self):
        """Test on_state_power updates power samples."""
        device_id = "test-device-1"
        now = time.time()
        
        self.monitor.on_state_power(device_id, "ON", 150.0, now)
        
        with self.monitor._lock:
            device_data = self.monitor._devices.get(device_id)
            self.assertEqual(len(device_data["power_samples"]), 1)
            self.assertEqual(device_data["power_samples"][0][1], 150.0)

    def test_trigger_off_sends_command(self):
        """Test trigger_off sends OFF command via MQTT."""
        device_id = "test-device-1"
        reason = "DRY_RUN"
        now = time.time()
        
        with patch.object(self.monitor, '_insert_event'):
            self.monitor._trigger_off(device_id, reason, now)
            
            # Verify MQTT command was sent
            self.mqtt_service.create_command.assert_called_once_with(device_id, "OFF")

    def test_clear_running_since(self):
        """Test clear_running_since resets running timer."""
        device_id = "test-device-1"
        now = time.time()
        
        # Set state to ON (starts running_since)
        self.monitor.on_state_power(device_id, "ON", 150.0, now)
        
        # Clear running_since
        self.monitor._clear_running_since(device_id)
        
        with self.monitor._lock:
            device_data = self.monitor._devices.get(device_id)
            self.assertIsNone(device_data["running_since"])


if __name__ == '__main__':
    unittest.main()
