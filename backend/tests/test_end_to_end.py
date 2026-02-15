"""
End-to-end integration tests.
Tests complete flows: device pairing, control, safety monitoring, Alexa.
"""
import json
import time
import unittest
import os
from unittest.mock import Mock, patch, MagicMock
from fastapi.testclient import TestClient
from datetime import datetime, timezone, timedelta

# Set test database before importing app
os.environ["DATABASE_URL"] = "sqlite:///./test_smartmotor.db"

from db import init_db, SessionLocal, engine, Base
from models import User, Device, DeviceOwnership, DevicePairingSession, DeviceEvent
from auth import hash_password, create_access_token, verify_password
from mqtt_service import MQTTService


class TestEndToEnd(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        """Set up test database once for all tests."""
        # Use test database
        os.environ["DATABASE_URL"] = "sqlite:///./test_smartmotor.db"
        
        # Drop all tables and recreate
        Base.metadata.drop_all(bind=engine)
        init_db()

    def setUp(self):
        """Set up test fixtures for each test."""
        # Clear database tables
        with SessionLocal() as db:
            db.query(DeviceEvent).delete()
            db.query(DevicePairingSession).delete()
            db.query(DeviceOwnership).delete()
            db.query(Device).delete()
            db.query(User).delete()
            db.commit()
        
        # Mock MQTT service
        self.mock_mqtt = Mock(spec=MQTTService)
        self.mock_mqtt.is_connected = True
        self.mock_mqtt.snapshot_states.return_value = {}
        self.mock_mqtt.create_command.return_value = "test-cmd-id-123"
        self.mock_mqtt.get_command.return_value = {
            "device_id": "test-device-1",
            "action": "ON",
            "status": "PENDING",
            "created_at_ts": time.time(),
            "updated_at_ts": time.time()
        }
        
        # Patch MQTT service in main module
        self.mqtt_patcher = patch('main.MQTTService')
        self.mock_mqtt_class = self.mqtt_patcher.start()
        self.mock_mqtt_class.from_env.return_value = self.mock_mqtt
        
        # Patch MotorMonitor
        self.monitor_patcher = patch('main.MotorMonitor')
        self.mock_monitor_class = self.monitor_patcher.start()
        self.mock_monitor = Mock()
        self.mock_monitor_class.return_value = self.mock_monitor
        
        # Import app after patching
        import importlib
        import main
        importlib.reload(main)
        self.app = main.app
        
        # Set mock MQTT in app state
        self.app.state.mqtt = self.mock_mqtt
        self.app.state.motor_monitor = self.mock_monitor
        
        # Create test client
        self.client = TestClient(self.app)

    def tearDown(self):
        """Clean up after each test."""
        self.mqtt_patcher.stop()
        self.monitor_patcher.stop()
        
        # Clear database
        with SessionLocal() as db:
            db.query(DeviceEvent).delete()
            db.query(DevicePairingSession).delete()
            db.query(DeviceOwnership).delete()
            db.query(Device).delete()
            db.query(User).delete()
            db.commit()

    @classmethod
    def tearDownClass(cls):
        """Clean up test database after all tests."""
        import os
        if os.path.exists("test_smartmotor.db"):
            os.remove("test_smartmotor.db")

    def test_user_registration_and_login(self):
        """Test complete user registration and login flow."""
        # Mock password hashing to avoid bcrypt version issues
        with patch('auth.hash_password') as mock_hash, \
             patch('auth.verify_password') as mock_verify:
            # Setup mocks
            mock_hash.return_value = "$2b$12$test_hashed_password"
            mock_verify.return_value = True
            
            # Register user
            register_response = self.client.post("/auth/register", json={
                "email": "test@example.com",
                "password": "test123"
            })
            self.assertEqual(register_response.status_code, 200)
            self.assertIn("id", register_response.json())
            
            # Login
            login_response = self.client.post("/auth/login", json={
                "email": "test@example.com",
                "password": "test123"
            })
            self.assertEqual(login_response.status_code, 200)
            data = login_response.json()
            self.assertIn("access_token", data)
            self.assertIn("refresh_token", data)
            self.assertIn("token_type", data)
            self.assertEqual(data["token_type"], "bearer")
            
            # Store token for other tests
            self._test_token = data["access_token"]

    def _get_auth_token(self):
        """Helper to get auth token."""
        with patch('auth.hash_password') as mock_hash, \
             patch('auth.verify_password') as mock_verify:
            mock_hash.return_value = "$2b$12$test_hashed_password"
            mock_verify.return_value = True
            
            # Register and login
            self.client.post("/auth/register", json={
                "email": "test@example.com",
                "password": "test123"
            })
            login_response = self.client.post("/auth/login", json={
                "email": "test@example.com",
                "password": "test123"
            })
            return login_response.json()["access_token"]

    def test_device_claiming_flow(self):
        """Test device pairing and claiming flow."""
        # Get auth token (creates user)
        token = self._get_auth_token()
        headers = {"Authorization": f"Bearer {token}"}
        
        # Get user ID from database
        db = SessionLocal()
        user = db.query(User).filter(User.email == "test@example.com").first()
        user_id = user.id
        db.close()
        
        # Create pairing session in database
        db = SessionLocal()
        pairing_session = DevicePairingSession(
            device_id="test-device-1",
            pair_code="ABC123",
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=5)
        )
        db.add(pairing_session)
        db.commit()
        db.close()
        
        # Claim device
        claim_response = self.client.post(
            "/devices/claim",
            json={"pair_code": "ABC123"},
            headers=headers
        )
        self.assertEqual(claim_response.status_code, 200)
        data = claim_response.json()
        self.assertEqual(data["status"], "claimed")
        self.assertEqual(data["device_id"], "test-device-1")
        
        # Verify device ownership was created
        db = SessionLocal()
        ownership = db.query(DeviceOwnership).filter(
            DeviceOwnership.user_id == user_id,
            DeviceOwnership.device_id == "test-device-1"
        ).first()
        self.assertIsNotNone(ownership)
        db.close()

    def test_device_list_flow(self):
        """Test getting list of user's devices."""
        token = self._get_auth_token()
        headers = {"Authorization": f"Bearer {token}"}
        
        db = SessionLocal()
        user = db.query(User).filter(User.email == "test@example.com").first()
        
        # Create device and ownership
        device = Device(id="test-device-1")
        db.add(device)
        ownership = DeviceOwnership(user_id=user.id, device_id="test-device-1", role="owner")
        db.add(ownership)
        db.commit()
        db.close()
        
        # Mock device state
        self.mock_mqtt.snapshot_states.return_value = {
            "test-device-1": {
                "last_seen_ts": time.time(),
                "online": True,
                "last_state": "OFF",
                "last_power": 0.0
            }
        }
        
        # Get devices
        devices_response = self.client.get("/devices", headers=headers)
        self.assertEqual(devices_response.status_code, 200)
        data = devices_response.json()
        self.assertIn("devices", data)
        self.assertEqual(len(data["devices"]), 1)
        self.assertEqual(data["devices"][0]["id"], "test-device-1")

    def test_device_control_flow(self):
        """Test device ON/OFF control flow."""
        # Get auth token and create device ownership
        token = self._get_auth_token()
        headers = {"Authorization": f"Bearer {token}"}
        
        db = SessionLocal()
        user = db.query(User).filter(User.email == "test@example.com").first()
        
        # Create device and ownership
        device = Device(id="test-device-1")
        db.add(device)
        ownership = DeviceOwnership(user_id=user.id, device_id="test-device-1", role="owner")
        db.add(ownership)
        db.commit()
        db.close()
        
        # Turn device ON
        on_response = self.client.post(
            "/devices/test-device-1/on",
            headers=headers
        )
        self.assertEqual(on_response.status_code, 200)
        
        # Verify MQTT command was sent
        self.mock_mqtt.create_command.assert_called()
        call_args = self.mock_mqtt.create_command.call_args
        self.assertEqual(call_args[0][0], "test-device-1")
        self.assertEqual(call_args[0][1], "ON")
        
        # Reset mock
        self.mock_mqtt.create_command.reset_mock()
        
        # Turn device OFF
        off_response = self.client.post(
            "/devices/test-device-1/off",
            headers=headers
        )
        self.assertEqual(off_response.status_code, 200)
        
        # Verify MQTT command was sent
        self.mock_mqtt.create_command.assert_called_with("test-device-1", "OFF")

    def test_device_command_status(self):
        """Test getting command status."""
        token = self._get_auth_token()
        headers = {"Authorization": f"Bearer {token}"}
        
        db = SessionLocal()
        user = db.query(User).filter(User.email == "test@example.com").first()
        
        device = Device(id="test-device-1")
        db.add(device)
        ownership = DeviceOwnership(user_id=user.id, device_id="test-device-1", role="owner")
        db.add(ownership)
        db.commit()
        db.close()
        
        # Mock command info
        self.mock_mqtt.get_command.return_value = {
            "device_id": "test-device-1",
            "action": "ON",
            "status": "SUCCESS",
            "created_at_ts": time.time(),
            "updated_at_ts": time.time()
        }
        
        # Get command status
        cmd_response = self.client.get(
            "/commands/test-cmd-id-123",
            headers=headers
        )
        self.assertEqual(cmd_response.status_code, 200)
        data = cmd_response.json()
        self.assertEqual(data["device_id"], "test-device-1")
        self.assertEqual(data["action"], "ON")
        self.assertEqual(data["status"], "SUCCESS")

    def test_alexa_discovery_flow(self):
        """Test Alexa device discovery."""
        # Get auth token and create device
        token = self._get_auth_token()
        headers = {"Authorization": f"Bearer {token}"}
        
        db = SessionLocal()
        user = db.query(User).filter(User.email == "test@example.com").first()
        
        device = Device(id="motor-1")
        db.add(device)
        ownership = DeviceOwnership(user_id=user.id, device_id="motor-1", role="owner")
        db.add(ownership)
        db.commit()
        db.close()
        
        # Alexa discovery request
        alexa_request = {
            "version": "3",
            "session": {},
            "context": {},
            "request": {
                "type": "Alexa.Discovery.Discover",
                "messageId": "test-message-id"
            }
        }
        
        discovery_response = self.client.post(
            "/alexa/smart-home",
            json=alexa_request,
            headers=headers
        )
        self.assertEqual(discovery_response.status_code, 200)
        
        response_data = discovery_response.json()
        self.assertIn("event", response_data)
        self.assertIn("header", response_data["event"])
        self.assertIn("payload", response_data["event"])
        
        # Check header
        header = response_data["event"]["header"]
        self.assertEqual(header["namespace"], "Alexa.Discovery")
        self.assertEqual(header["name"], "Discover.Response")
        
        # Check endpoints
        endpoints = response_data["event"]["payload"]["endpoints"]
        self.assertEqual(len(endpoints), 1)
        self.assertEqual(endpoints[0]["endpointId"], "motor-1")
        self.assertEqual(endpoints[0]["manufacturerName"], "SmartMotor")
        self.assertIn("capabilities", endpoints[0])

    def test_alexa_power_control_flow(self):
        """Test Alexa power control (TurnOn/TurnOff)."""
        # Get auth token and create device
        token = self._get_auth_token()
        headers = {"Authorization": f"Bearer {token}"}
        
        db = SessionLocal()
        user = db.query(User).filter(User.email == "test@example.com").first()
        
        device = Device(id="motor-1")
        db.add(device)
        ownership = DeviceOwnership(user_id=user.id, device_id="motor-1", role="owner")
        db.add(ownership)
        db.commit()
        db.close()
        
        # Alexa TurnOn request
        alexa_request = {
            "version": "3",
            "session": {},
            "context": {},
            "request": {
                "type": "Alexa.PowerController",
                "endpoint": {
                    "endpointId": "motor-1"
                },
                "directive": {
                    "name": "TurnOn"
                },
                "messageId": "test-message-id",
                "correlationToken": "test-token"
            }
        }
        
        control_response = self.client.post(
            "/alexa/smart-home",
            json=alexa_request,
            headers=headers
        )
        self.assertEqual(control_response.status_code, 200)
        
        # Verify MQTT command was sent
        self.mock_mqtt.create_command.assert_called_with("motor-1", "ON")
        
        # Check response structure
        response_data = control_response.json()
        self.assertIn("event", response_data)
        self.assertIn("context", response_data)
        
        # Check context properties
        properties = response_data["context"]["properties"]
        self.assertEqual(len(properties), 1)
        self.assertEqual(properties[0]["namespace"], "Alexa.PowerController")
        self.assertEqual(properties[0]["name"], "powerState")
        self.assertEqual(properties[0]["value"], "ON")

    def test_alexa_turn_off(self):
        """Test Alexa TurnOff command."""
        token = self._get_auth_token()
        headers = {"Authorization": f"Bearer {token}"}
        
        db = SessionLocal()
        user = db.query(User).filter(User.email == "test@example.com").first()
        
        device = Device(id="motor-1")
        db.add(device)
        ownership = DeviceOwnership(user_id=user.id, device_id="motor-1", role="owner")
        db.add(ownership)
        db.commit()
        db.close()
        
        alexa_request = {
            "version": "3",
            "session": {},
            "context": {},
            "request": {
                "type": "Alexa.PowerController",
                "endpoint": {"endpointId": "motor-1"},
                "directive": {"name": "TurnOff"},
                "messageId": "test-123",
                "correlationToken": "test-token"
            }
        }
        
        response = self.client.post(
            "/alexa/smart-home",
            json=alexa_request,
            headers=headers
        )
        self.assertEqual(response.status_code, 200)
        self.mock_mqtt.create_command.assert_called_with("motor-1", "OFF")
        
        properties = response.json()["context"]["properties"]
        self.assertEqual(properties[0]["value"], "OFF")

    def test_health_endpoint(self):
        """Test health check endpoint."""
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("status", data)
        self.assertIn("mqtt_connected", data)
        self.assertIn("known_devices", data)
        self.assertEqual(data["status"], "ok")

    def test_unauthorized_access(self):
        """Test that unauthorized requests are rejected."""
        # Try to access devices without token
        response = self.client.get("/devices")
        self.assertEqual(response.status_code, 401)
        
        # Try with invalid token
        response = self.client.get(
            "/devices",
            headers={"Authorization": "Bearer invalid-token"}
        )
        self.assertEqual(response.status_code, 401)

    def test_device_not_owned(self):
        """Test that users can't control devices they don't own."""
        token = self._get_auth_token()
        headers = {"Authorization": f"Bearer {token}"}
        
        # Try to control device that doesn't exist or isn't owned
        response = self.client.post(
            "/devices/other-device/on",
            headers=headers
        )
        # Should return 404 (device not found or not owned)
        self.assertIn(response.status_code, [404, 403])


if __name__ == '__main__':
    unittest.main()
