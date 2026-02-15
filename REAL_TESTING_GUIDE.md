# Real Testing Guide - No Mocks!

## 🎯 What's Mocked in Tests vs Real Testing

### What Tests Mock:
1. **MQTT Service** - Mocked to avoid network calls
2. **Motor Monitor** - Mocked background thread
3. **Password Hashing** - Mocked to avoid bcrypt version issues
4. **Database** - Uses test database (test_smartmotor.db)

### Real Testing - No Mocks!

---

## 🚀 Step-by-Step Real Testing

### 1. Start Backend with Real MQTT

```bash
cd backend
source .venv/bin/activate

# Set MQTT broker (or use defaults)
export MQTT_BROKER=broker.hivemq.com
export MQTT_PORT=1883

# Start server
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

**What happens:**
- Backend connects to REAL MQTT broker
- Subscribes to device topics
- Waits for real device messages

---

### 2. Test with Fake Device (Simulates Real ESP32)

```bash
cd backend
source .venv/bin/activate
python3 fake_device.py
```

**What it does:**
- Connects to REAL MQTT broker
- Publishes heartbeat every 10s
- Publishes state every 15s
- Publishes power every 5s
- Listens for commands
- Sends ACKs

**You'll see:**
```
Connected to broker 0
heartbeat
state: OFF
power: 123.4
COMMAND RECEIVED: {"command_id":"...","action":"ON"}
ACK SENT: {...}
```

---

### 3. Test with Real ESP32

**Flash ESP32 firmware:**
- Use Arduino IDE or PlatformIO
- Configure WiFi credentials
- Configure MQTT broker address
- Upload code

**ESP32 will:**
- Connect to WiFi
- Connect to MQTT broker
- Start publishing heartbeat/state/power
- Listen for commands

**Check it's working:**
```bash
# Check backend health
curl http://localhost:8000/health

# Should show your ESP32 device ID in "known_devices"
```

---

### 4. Test API Endpoints (Real Backend)

**Register user:**
```bash
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test123"}'
```

**Login (get token):**
```bash
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test123"}'

# Save the access_token from response
```

**Claim device (use pairing code from fake_device.py or ESP32 Serial Monitor):**
```bash
curl -X POST http://localhost:8000/devices/claim \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{"pair_code":"ABC123"}'
```

**Get devices:**
```bash
curl http://localhost:8000/devices \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Turn device ON:**
```bash
curl -X POST http://localhost:8000/devices/TEST_MOTOR_1/on \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Turn device OFF:**
```bash
curl -X POST http://localhost:8000/devices/TEST_MOTOR_1/off \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

---

### 5. Test with Android App (Real)

1. **Build and install app** on Android device/emulator
2. **Configure backend URL:**
   - Debug: `http://10.0.2.2:8000` (emulator)
   - Real device: `http://YOUR_COMPUTER_IP:8000`
3. **Register/Login** in app
4. **Add device** using pairing code
5. **Control device** from app

---

### 6. Test Alexa Integration (Real)

**Prerequisites:**
- Backend must be publicly accessible (HTTPS)
- Alexa skill configured

**Test discovery:**
```bash
curl -X POST http://localhost:8000/alexa/smart-home \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "version": "3",
    "session": {},
    "context": {},
    "request": {
      "type": "Alexa.Discovery.Discover",
      "messageId": "test-123"
    }
  }'
```

**Test control:**
```bash
curl -X POST http://localhost:8000/alexa/smart-home \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "version": "3",
    "session": {},
    "context": {},
    "request": {
      "type": "Alexa.PowerController",
      "endpoint": {"endpointId": "TEST_MOTOR_1"},
      "directive": {"name": "TurnOn"},
      "messageId": "test-123"
    }
  }'
```

---

## 📊 Real vs Mock Comparison

| Component | Tests (Mocked) | Real Testing |
|-----------|---------------|--------------|
| MQTT Broker | Mock object | Real broker (broker.hivemq.com) |
| MQTT Messages | Simulated | Real MQTT pub/sub |
| Device | Mock state | Real ESP32 or fake_device.py |
| Database | test_smartmotor.db | smartmotor.db |
| Password Hashing | Mocked | Real bcrypt (works in production) |
| Network | No network calls | Real HTTP/MQTT |

---

## 🔍 Monitoring Real Testing

### Check Backend Logs:
```bash
# Watch backend logs
tail -f backend.log  # or console output
```

**Look for:**
- `MQTT connected`
- `MQTT message topic=/devices/...`
- `Publishing MQTT command`

### Check MQTT Messages:
Use MQTT client (MQTT.fx, mosquitto_sub):
```bash
mosquitto_sub -h broker.hivemq.com -t "/devices/#"
```

**You'll see:**
- Heartbeat messages
- State updates
- Power readings
- Commands
- ACKs

### Check Database:
```bash
sqlite3 backend/smartmotor.db
.tables
SELECT * FROM devices;
SELECT * FROM device_ownerships;
SELECT * FROM device_events;
```

---

## 🎯 Quick Real Test Flow

1. **Start backend:**
   ```bash
   cd backend && source .venv/bin/activate
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

2. **Start fake device (in another terminal):**
   ```bash
   cd backend && source .venv/bin/activate
   python3 fake_device.py
   ```

3. **Register & login:**
   ```bash
   curl -X POST http://localhost:8000/auth/register \
     -H "Content-Type: application/json" \
     -d '{"email":"test@test.com","password":"test123"}'
   
   curl -X POST http://localhost:8000/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"test@test.com","password":"test123"}'
   ```

4. **Claim device (check fake_device.py output for pairing code):**
   ```bash
   curl -X POST http://localhost:8000/devices/claim \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -d '{"pair_code":"PAIRING_CODE_HERE"}'
   ```

5. **Control device:**
   ```bash
   curl -X POST http://localhost:8000/devices/TEST_MOTOR_1/on \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```

6. **Watch fake_device.py output** - you should see:
   ```
   COMMAND RECEIVED: {"command_id":"...","action":"ON"}
   ACK SENT: {...}
   ```

7. **Check device state:**
   ```bash
   curl http://localhost:8000/devices \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```

---

## ✅ Real Testing Checklist

- [ ] Backend starts and connects to MQTT
- [ ] Fake device or ESP32 connects to MQTT
- [ ] Device appears in `/health` endpoint
- [ ] Can register and login user
- [ ] Can claim device with pairing code
- [ ] Can turn device ON/OFF via API
- [ ] Device receives commands and sends ACK
- [ ] State updates appear in API
- [ ] Android app can connect and control
- [ ] Alexa integration works (if configured)

---

## 🐛 Troubleshooting Real Testing

### MQTT Connection Fails
- Check internet connection
- Verify broker address: `broker.hivemq.com:1883`
- Check firewall settings
- Try different broker

### Device Not Appearing
- Check MQTT topics match exactly
- Verify device is publishing heartbeat
- Check backend logs for MQTT messages
- Verify device ID matches

### Commands Not Working
- Check device is subscribed to command topic
- Verify JSON format in commands
- Check device logs/Serial Monitor
- Verify device ownership in database

---

**That's it! Now you're testing with REAL components, not mocks!** 🎉
