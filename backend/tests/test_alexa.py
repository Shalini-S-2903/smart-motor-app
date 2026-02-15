"""
Unit tests for Alexa integration.
Tests discovery, power control, and request/response format.
"""
import unittest
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime, timezone

from alexa_routes import handle_discovery, handle_power_control, AlexaRequest
from models import User, Device, DeviceOwnership
from db import Base


class TestAlexaIntegration(unittest.TestCase):
    def setUp(self):
        """Set up test fixtures."""
        self.user = Mock(spec=User)
        self.user.id = 1
        self.user.email = "test@example.com"
        
        self.device = Mock(spec=Device)
        self.device.id = "motor-1"
        
        self.ownership = Mock(spec=DeviceOwnership)
        self.ownership.device_id = "motor-1"
        self.ownership.user_id = 1

    def test_discovery_response_format(self):
        """Test Alexa discovery response has correct format."""
        request = AlexaRequest(
            version="3",
            session={},
            context={},
            request={
                "type": "Alexa.Discovery.Discover",
                "messageId": "test-message-id"
            }
        )
        
        mock_db = Mock()
        
        # First query: db.query(DeviceOwnership).filter(...).all()
        ownership_filter_result = Mock()
        ownership_filter_result.all.return_value = [self.ownership]
        ownership_query = Mock()
        ownership_query.filter.return_value = ownership_filter_result
        
        # Second query: db.query(Device).filter(Device.id.in_(...)).all()
        # The filter gets Device.id.in_(device_ids) as argument
        device_filter_result = Mock()
        device_filter_result.all.return_value = [self.device]
        device_query = Mock()
        device_query.filter.return_value = device_filter_result
        
        # Return different query mocks based on model
        def query_side_effect(model):
            if model == DeviceOwnership:
                return ownership_query
            elif model == Device:
                return device_query
            return Mock()
        
        mock_db.query.side_effect = query_side_effect
        
        response = handle_discovery(request, self.user, mock_db)
        
        # Check response structure
        self.assertIn("event", response)
        self.assertIn("header", response["event"])
        self.assertIn("payload", response["event"])
        
        # Check header
        header = response["event"]["header"]
        self.assertEqual(header["namespace"], "Alexa.Discovery")
        self.assertEqual(header["name"], "Discover.Response")
        
        # Check endpoints
        endpoints = response["event"]["payload"]["endpoints"]
        self.assertEqual(len(endpoints), 1)
        
        endpoint = endpoints[0]
        self.assertEqual(endpoint["endpointId"], "motor-1")
        self.assertEqual(endpoint["manufacturerName"], "SmartMotor")
        self.assertIn("capabilities", endpoint)
        
        # Check capabilities
        capabilities = endpoint["capabilities"]
        self.assertTrue(any(cap["interface"] == "Alexa.PowerController" for cap in capabilities))
        self.assertTrue(any(cap["interface"] == "Alexa.EndpointHealth" for cap in capabilities))

    def test_power_control_turn_on(self):
        """Test Alexa TurnOn command."""
        request = AlexaRequest(
            version="3",
            session={},
            context={},
            request={
                "endpoint": {
                    "endpointId": "motor-1"
                },
                "directive": {
                    "name": "TurnOn"
                },
                "messageId": "test-message-id",
                "correlationToken": "test-token"
            }
        )
        
        mock_db = Mock()
        ownership_query = Mock()
        ownership_query.filter.return_value.first.return_value = self.ownership
        mock_db.query.return_value = ownership_query
        
        mock_mqtt = Mock()
        mock_mqtt.create_command.return_value = "cmd-123"
        
        # Mock the main.app import that alexa_routes uses internally
        with patch('main.app') as mock_app:
            mock_app.state.mqtt = mock_mqtt
            
            response = handle_power_control(request, self.user, mock_db)
            
            # Verify MQTT command was sent
            mock_mqtt.create_command.assert_called_once_with("motor-1", "ON")
            
            # Check response structure
            self.assertIn("event", response)
            self.assertIn("context", response)
            
            # Check event header
            header = response["event"]["header"]
            self.assertEqual(header["namespace"], "Alexa")
            self.assertEqual(header["name"], "Response")
            
            # Check context properties
            properties = response["context"]["properties"]
            self.assertEqual(len(properties), 1)
            self.assertEqual(properties[0]["namespace"], "Alexa.PowerController")
            self.assertEqual(properties[0]["name"], "powerState")
            self.assertEqual(properties[0]["value"], "ON")

    def test_power_control_turn_off(self):
        """Test Alexa TurnOff command."""
        request = AlexaRequest(
            version="3",
            session={},
            context={},
            request={
                "endpoint": {
                    "endpointId": "motor-1"
                },
                "directive": {
                    "name": "TurnOff"
                },
                "messageId": "test-message-id",
                "correlationToken": "test-token"
            }
        )
        
        mock_db = Mock()
        ownership_query = Mock()
        ownership_query.filter.return_value.first.return_value = self.ownership
        mock_db.query.return_value = ownership_query
        
        mock_mqtt = Mock()
        mock_mqtt.create_command.return_value = "cmd-123"
        
        # Mock the main.app import that alexa_routes uses internally
        with patch('main.app') as mock_app:
            mock_app.state.mqtt = mock_mqtt
            
            response = handle_power_control(request, self.user, mock_db)
            
            # Verify MQTT command was sent
            mock_mqtt.create_command.assert_called_once_with("motor-1", "OFF")
            
            # Check response
            properties = response["context"]["properties"]
            self.assertEqual(properties[0]["value"], "OFF")

    def test_power_control_device_not_owned(self):
        """Test Alexa command fails when device not owned by user."""
        from fastapi import HTTPException
        
        request = AlexaRequest(
            version="3",
            session={},
            context={},
            request={
                "endpoint": {
                    "endpointId": "motor-1"
                },
                "directive": {
                    "name": "TurnOn"
                },
                "messageId": "test-message-id"
            }
        )
        
        mock_db = Mock()
        ownership_query = Mock()
        ownership_query.filter.return_value.first.return_value = None  # No ownership
        mock_db.query.return_value = ownership_query
        
        with self.assertRaises(HTTPException) as context:
            handle_power_control(request, self.user, mock_db)
        
        self.assertEqual(context.exception.status_code, 404)


if __name__ == '__main__':
    unittest.main()
