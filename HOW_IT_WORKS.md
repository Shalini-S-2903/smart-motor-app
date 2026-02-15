# How SmartMotor System Works - Brief Summary

## 🏗️ Architecture Overview

```
ESP32 Device → MQTT Broker → FastAPI Backend → Android App / Alexa
     ↓              ↓              ↓                    ↓
  Motor/Relay   Message Bus    REST API          User Interface
```

---

## 🔄 Complete Flow

### 1. Device Connection
- **ESP32** connects to WiFi
- **ESP32** connects to **MQTT broker** (broker.hivemq.com)
- **ESP32** starts publishing:
  - Heartbeat every 10s (keeps device "online")
  - State when changed (ON/OFF)
  - Power readings every 5s (watts)

### 2. Backend Connection
- **FastAPI backend** connects to same MQTT broker
- **Backend** subscribes to device topics:
  - `/devices/{id}/heartbeat`
  - `/devices/{id}/state`
  - `/devices/{id}/power`
  - `/devices/{id}/ack`
  - `/devices/{id}/pairing`
- **Backend** tracks device state in memory
- **Backend** stores history in SQLite database

### 3. User Control Flow

**User turns motor ON via Android app:**

```
Android App
    ↓ (POST /devices/{id}/on)
Backend API
    ↓ (Validates user owns device)
Backend MQTT Service
    ↓ (Publishes to /devices/{id}/command)
MQTT Broker
    ↓ (Routes message)
ESP32 Device
    ↓ (Receives command)
ESP32 GPIO
    ↓ (Turns relay ON)
Motor turns ON
    ↓
ESP32 publishes state="ON"
    ↓
Backend receives state update
    ↓
Backend broadcasts via SSE
    ↓
Android App updates UI
```

### 4. Safety Monitoring

**MotorMonitor** (background thread) checks every 5 seconds:

1. **DRY_RUN Detection:**
   - If motor ON but power < 40W for 15s
   - → Auto sends OFF command (no water detected)

2. **SAFETY_TIMER:**
   - If motor runs > 20 minutes
   - → Auto sends OFF command (prevent damage)

3. **TANK_FULL Detection:**
   - If power drops 60% after stable run
   - → Auto sends OFF command (tank full)

---

## 📡 MQTT Protocol

### Device → Backend (Publishes)
- **Heartbeat:** `/devices/{id}/heartbeat` - Any string, every 10s
- **State:** `/devices/{id}/state` - "ON" or "OFF"
- **Power:** `/devices/{id}/power` - Float as string (e.g., "150.5")
- **ACK:** `/devices/{id}/ack` - `{"command_id":"...","status":"SUCCESS"}`
- **Pairing:** `/devices/{id}/pairing` - Pairing code string

### Backend → Device (Publishes)
- **Command:** `/devices/{id}/command` - `{"command_id":"...","action":"ON"}`

---

## 🔐 Authentication & Security

1. **User Registration:**
   - Email + password → Bcrypt hash → Stored in database

2. **Login:**
   - Email + password → Verify hash → Generate JWT token

3. **API Requests:**
   - Include JWT token in `Authorization: Bearer {token}` header
   - Backend validates token → Gets user → Checks device ownership

4. **Device Ownership:**
   - Users can only control devices they own
   - Ownership created when device is "claimed" with pairing code

---

## 💾 Database

**SQLite** (`smartmotor.db`):

- **users** - User accounts
- **devices** - Device metadata
- **device_ownerships** - User-device relationships
- **device_events** - Safety events (DRY_RUN, SAFETY_TIMER, etc.)
- **commands** - Command history
- **device_state_history** - State/power history
- **device_pairing_sessions** - Temporary pairing codes

---

## 📱 Android App

1. **Login/Register** → Get JWT token
2. **Store token** securely (Android Keystore)
3. **Fetch devices** → GET /devices
4. **Connect to SSE** → GET /events (real-time updates)
5. **Control device** → POST /devices/{id}/on or /off
6. **Add device** → Enter pairing code → POST /devices/claim

---

## 🎤 Alexa Integration

1. **Discovery:**
   - Alexa asks: "What devices do you have?"
   - Backend returns list of user's devices
   - Alexa shows devices in app

2. **Control:**
   - User: "Alexa, turn on Motor 1"
   - Alexa → Backend: POST /alexa/smart-home (TurnOn request)
   - Backend → MQTT: Send ON command
   - Backend → Alexa: Success response
   - Device executes → Sends ACK

---

## ⚡ Real-Time Updates (SSE)

**Server-Sent Events** at `/events`:

- Backend broadcasts device updates to all connected clients
- Android app receives real-time state changes
- No polling needed - instant updates

**Events:**
- `device_update` - State/power changed
- `device_event` - Safety event (DRY_RUN, etc.)

---

## 🧪 Testing

### Unit Tests (Mocked):
- Mock MQTT service
- Mock database
- Test individual components

### Real Testing:
- Real MQTT broker
- Real device (ESP32 or fake_device.py)
- Real HTTP requests
- Real database

---

## 🎯 Key Components

1. **MQTTService** - Handles MQTT communication
2. **MotorMonitor** - Safety monitoring engine
3. **FastAPI** - REST API server
4. **SQLite** - Data persistence
5. **JWT** - Authentication
6. **SSE** - Real-time updates
7. **Android App** - User interface
8. **Alexa Routes** - Voice control

---

## 🚀 Quick Start

1. **Start backend:**
   ```bash
   cd backend
   source .venv/bin/activate
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

2. **Start fake device:**
   ```bash
   python3 fake_device.py
   ```

3. **Test API:**
   ```bash
   # Register, login, claim device, control
   ```

4. **Use Android app** or **Alexa** to control devices

---

**That's the complete system in a nutshell!** 🎉
