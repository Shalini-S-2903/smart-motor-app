"""
Unit tests for Alexa integration.
Tests discovery, power control, and request/response format.
"""
import unittest
from unittest.mock import Mock, patch

from alexa_routes import handle_discovery, handle_power_control
from models import User, Device, DeviceOwnership

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
        header = {
            "namespace": "Alexa.Discovery",
            "name": "Discover",
            "messageId": "test-message-id"
        }
        
        mock_db = Mock()
        ownership_filter_result = Mock()
        ownership_filter_result.all.return_value = [self.ownership]
        ownership_query = Mock()
        ownership_query.filter.return_value = ownership_filter_result
        
        device_filter_result = Mock()
        device_filter_result.all.return_value = [self.device]
        device_query = Mock()
        device_query.filter.return_value = device_filter_result
        
        def query_side_effect(model):
            if model == DeviceOwnership:
                return ownership_query
            elif model == Device:
                return device_query
            return Mock()
        
        mock_db.query.side_effect = query_side_effect
        
        response = handle_discovery(header, self.user, mock_db)
        
        self.assertIn("event", response)
        self.assertIn("header", response["event"])
        self.assertIn("payload", response["event"])
        
        resp_header = response["event"]["header"]
        self.assertEqual(resp_header["namespace"], "Alexa.Discovery")
        self.assertEqual(resp_header["name"], "Discover.Response")
        
        endpoints = response["event"]["payload"]["endpoints"]
        self.assertEqual(len(endpoints), 1)
        
        endpoint = endpoints[0]
        self.assertEqual(endpoint["endpointId"], "motor-1")

    def test_power_control_turn_on(self):
        """Test Alexa TurnOn command."""
        directive = {
            "header": {
                "namespace": "Alexa.PowerController",
                "name": "TurnOn",
                "messageId": "test-message-id",
                "correlationToken": "test-token"
            },
            "endpoint": {
                "endpointId": "motor-1",
            }
        }
        
        mock_db = Mock()
        ownership_query = Mock()
        ownership_query.filter.return_value.first.return_value = self.ownership
        mock_db.query.return_value = ownership_query
        
        mock_mqtt = Mock()
        mock_mqtt.create_command.return_value = "cmd-123"
        
        with patch('main.app') as mock_app:
            mock_app.state.mqtt = mock_mqtt
            response = handle_power_control(directive, self.user, mock_db)
            
            mock_mqtt.create_command.assert_called_once_with("motor-1", "ON")
            
            self.assertIn("event", response)
            self.assertIn("context", response)
            
            resp_header = response["event"]["header"]
            self.assertEqual(resp_header["namespace"], "Alexa")
            self.assertEqual(resp_header["name"], "Response")
            
            properties = response["context"]["properties"]
            self.assertEqual(properties[0]["namespace"], "Alexa.PowerController")
            self.assertEqual(properties[0]["name"], "powerState")
            self.assertEqual(properties[0]["value"], "ON")

    def test_power_control_turn_off(self):
        """Test Alexa TurnOff command."""
        directive = {
            "header": {
                "namespace": "Alexa.PowerController",
                "name": "TurnOff",
                "messageId": "test-message-id",
                "correlationToken": "test-token"
            },
            "endpoint": {
                "endpointId": "motor-1",
            }
        }
        
        mock_db = Mock()
        ownership_query = Mock()
        ownership_query.filter.return_value.first.return_value = self.ownership
        mock_db.query.return_value = ownership_query
        
        mock_mqtt = Mock()
        mock_mqtt.create_command.return_value = "cmd-123"
        
        with patch('main.app') as mock_app:
            mock_app.state.mqtt = mock_mqtt
            response = handle_power_control(directive, self.user, mock_db)
            
            mock_mqtt.create_command.assert_called_once_with("motor-1", "OFF")
            
            properties = response["context"]["properties"]
            self.assertEqual(properties[0]["value"], "OFF")

    def test_power_control_device_not_owned(self):
        """Test Alexa command fails when device not owned by user."""
        from fastapi import HTTPException
        directive = {
            "header": {
                "namespace": "Alexa.PowerController",
                "name": "TurnOn",
                "messageId": "test-message-id"
            },
            "endpoint": {
                "endpointId": "motor-1"
            }
        }
        
        mock_db = Mock()
        ownership_query = Mock()
        ownership_query.filter.return_value.first.return_value = None
        mock_db.query.return_value = ownership_query
        
        response = handle_power_control(directive, self.user, mock_db)
        
        self.assertIn("event", response)
        header = response["event"]["header"]
        self.assertEqual(header["name"], "ErrorResponse")
        
        payload = response["event"]["payload"]
        self.assertEqual(payload["type"], "NO_SUCH_ENDPOINT")

if __name__ == '__main__':
    unittest.main()
