"""
Unit tests for MQTTService.
Tests device state tracking, command creation, and MQTT message handling.
"""
import json
import time
import unittest
from unittest.mock import Mock, patch, MagicMock
import threading

from mqtt_service import MQTTService


class TestMQTTService(unittest.TestCase):
    def setUp(self):
        """Set up test MQTT service with mock broker."""
        self.service = MQTTService(
            broker="test-broker",
            port=1883,
            username=None,
            password=None
        )
        # Mock the MQTT client to avoid actual network calls
        self.service.client = Mock()
        self.service.client.publish = Mock()
        self.service.is_connected = True

    def test_snapshot_states_empty(self):
        """Test snapshot_states returns empty dict when no devices."""
        states = self.service.snapshot_states()
        self.assertEqual(states, {})

    def test_create_command(self):
        """Test command creation and MQTT publish."""
        device_id = "test-device-1"
        action = "ON"
        
        cmd_id = self.service.create_command(device_id, action)
        
        # Command ID should be a UUID string
        self.assertIsInstance(cmd_id, str)
        self.assertEqual(len(cmd_id), 36)  # UUID format
        
        # Verify command was stored
        cmd_info = self.service.get_command(cmd_id)
        self.assertIsNotNone(cmd_info)
        self.assertEqual(cmd_info["device_id"], device_id)
        self.assertEqual(cmd_info["action"], action)
        self.assertEqual(cmd_info["status"], "PENDING")
        
        # Verify MQTT publish was called
        self.service.client.publish.assert_called_once()
        call_args = self.service.client.publish.call_args
        self.assertEqual(call_args[0][0], f"devices/{device_id}/command")
        
        # Verify payload
        payload = json.loads(call_args[0][1])
        self.assertEqual(payload["command_id"], cmd_id)
        self.assertEqual(payload["action"], action)

    def test_handle_heartbeat_message(self):
        """Test handling heartbeat MQTT message."""
        device_id = "test-device-1"
        topic = f"/devices/{device_id}/heartbeat"
        payload = str(time.time())
        
        msg = Mock()
        msg.topic = topic
        msg.payload = payload.encode()
        
        self.service._on_message(self.service.client, None, msg)
        
        # Check state was updated
        states = self.service.snapshot_states()
        self.assertIn(device_id, states)
        self.assertIn("last_seen_ts", states[device_id])
        self.assertIsNotNone(states[device_id]["last_seen_ts"])

    def test_handle_state_message(self):
        """Test handling state MQTT message."""
        device_id = "test-device-1"
        topic = f"/devices/{device_id}/state"
        payload = "ON"
        
        msg = Mock()
        msg.topic = topic
        msg.payload = payload.encode()
        
        self.service._on_message(self.service.client, None, msg)
        
        # Check state was updated
        states = self.service.snapshot_states()
        self.assertIn(device_id, states)
        self.assertEqual(states[device_id]["last_state"], "ON")

    def test_handle_power_message(self):
        """Test handling power MQTT message."""
        device_id = "test-device-1"
        topic = f"/devices/{device_id}/power"
        payload = "150.5"
        
        msg = Mock()
        msg.topic = topic
        msg.payload = payload.encode()
        
        self.service._on_message(self.service.client, None, msg)
        
        # Check power was updated
        states = self.service.snapshot_states()
        self.assertIn(device_id, states)
        self.assertEqual(states[device_id]["last_power"], 150.5)

    def test_handle_ack_message(self):
        """Test handling ACK MQTT message."""
        device_id = "test-device-1"
        
        # First create a command
        cmd_id = self.service.create_command(device_id, "ON")
        
        # Now send ACK
        topic = f"/devices/{device_id}/ack"
        payload = json.dumps({"command_id": cmd_id, "status": "SUCCESS"})
        
        msg = Mock()
        msg.topic = topic
        msg.payload = payload.encode()
        
        self.service._on_message(self.service.client, None, msg)
        
        # Check command status was updated
        cmd_info = self.service.get_command(cmd_id)
        self.assertEqual(cmd_info["status"], "SUCCESS")

    def test_handle_pairing_message(self):
        """Test handling pairing MQTT message."""
        device_id = "test-device-1"
        topic = f"/devices/{device_id}/pairing"
        payload = "ABC123"
        
        msg = Mock()
        msg.topic = topic
        msg.payload = payload.encode()
        
        with patch('mqtt_service.SessionLocal') as mock_session_local:
            mock_session = Mock()
            mock_session_local.return_value = mock_session
            mock_session.__enter__ = Mock(return_value=mock_session)
            mock_session.__exit__ = Mock(return_value=None)
            mock_session.query.return_value.filter.return_value.first.return_value = None
            
            self.service._on_message(self.service.client, None, msg)
            
            # Verify pairing session was created or updated
            # The function may call add() or update existing, so check if session was used
            self.assertTrue(mock_session.add.called or mock_session.commit.called)

    def test_simulate_heartbeat(self):
        """Test simulate_heartbeat helper."""
        device_id = "test-device-1"
        
        self.service.simulate_heartbeat(device_id)
        
        states = self.service.snapshot_states()
        self.assertIn(device_id, states)
        self.assertIn("last_seen_ts", states[device_id])

    def test_simulate_state(self):
        """Test simulate_state helper."""
        device_id = "test-device-1"
        
        self.service.simulate_state(device_id, "ON", 150.5)
        
        states = self.service.snapshot_states()
        self.assertIn(device_id, states)
        self.assertEqual(states[device_id]["last_state"], "ON")
        self.assertEqual(states[device_id]["last_power"], 150.5)

    def test_simulate_ack(self):
        """Test simulate_ack helper."""
        device_id = "test-device-1"
        cmd_id = self.service.create_command(device_id, "ON")
        
        self.service.simulate_ack(cmd_id)
        
        cmd_info = self.service.get_command(cmd_id)
        self.assertEqual(cmd_info["status"], "SUCCESS")

    def test_from_env(self):
        """Test creating service from environment variables."""
        with patch.dict('os.environ', {
            'MQTT_BROKER': 'custom-broker',
            'MQTT_PORT': '8883',
            'MQTT_USERNAME': 'user',
            'MQTT_PASSWORD': 'pass'
        }):
            service = MQTTService.from_env()
            self.assertEqual(service.broker, 'custom-broker')
            self.assertEqual(service.port, 8883)
            self.assertEqual(service.username, 'user')
            self.assertEqual(service.password, 'pass')


if __name__ == '__main__':
    unittest.main()
