# Full system test (no real ESP32)

You have a 3-layer IoT system:

**Android App → FastAPI Backend → MQTT Broker → ESP32 Device**  
*(DB state + realtime MQTT state)*

Before an ESP32 exists, we **fake** the device so you can test end-to-end.

---

## Step 1 — Create a fake device (manual provisioning)

Open a **new terminal** (keep the backend running in the first one).

```bash
cd backend
source venv/bin/activate
python
```

Then paste:

```python
from db import SessionLocal
from models import Device

db = SessionLocal()

device = Device(id="TEST_MOTOR_1")
db.add(device)
db.commit()
db.close()

print("Device created")
```

Exit Python:

```text
exit()
```

---

## Step 2 — Make it claimable (pair code)

We simulate the installer giving the user a pairing code. The app will claim by this code only.

Run Python again:

```python
from datetime import datetime, timezone, timedelta
from db import SessionLocal
from models import Device, DevicePairingSession
import random

db = SessionLocal()

code = str(random.randint(100000, 999999))
expires_at = datetime.now(tz=timezone.utc) + timedelta(minutes=5)

pair = DevicePairingSession(
    device_id="TEST_MOTOR_1",
    pair_code=code,
    expires_at=expires_at,
)
db.add(pair)
db.commit()
db.close()

print("PAIR CODE:", code)
```

Copy the printed 6-digit code.

---

## Step 3 — Claim from the app

1. In the app: **Add Device** → enter the pair code → **Claim Device**.
2. You should be taken to **Home** and see a device card for `TEST_MOTOR_1` (once the backend includes it in the devices list for your user).

---

## Step 4 — Simulate the ESP32 (live MQTT)

So the backend (and app via SSE) see the device as online and get state/power.

Install MQTT client (macOS):

```bash
brew install mosquitto
```

In another terminal, publish as if the motor controller is sending data:

**Heartbeat (device online):**

```bash
mosquitto_pub -h broker.hivemq.com -t /devices/TEST_MOTOR_1/heartbeat -m "1"
```

**Power (watts):**

```bash
mosquitto_pub -h broker.hivemq.com -t /devices/TEST_MOTOR_1/power -m "230.5"
```

**State (ON/OFF):**

```bash
mosquitto_pub -h broker.hivemq.com -t /devices/TEST_MOTOR_1/state -m "ON"
```

Watch the app — the device card should update **live** (online, power, state).

---

## Step 5 — Test button control (round trip)

1. In the app, tap **ON** for `TEST_MOTOR_1`.
2. The backend publishes to:
   - Topic: `/devices/TEST_MOTOR_1/command`
   - Payload: `{"command_id":"<uuid>","action":"ON"}`
3. In the **backend terminal** logs you’ll see the `command_id` for that command.

Simulate the ESP32 acknowledging the command:

```bash
mosquitto_pub -h broker.hivemq.com -t /devices/TEST_MOTOR_1/ack -m '{"command_id":"PUT_ID_FROM_BACKEND_LOG","status":"SUCCESS"}'
```

Replace `PUT_ID_FROM_BACKEND_LOG` with the actual `command_id` from the backend log. After the ack, the app should reflect the command as successful.

---

## Summary

| Step | What you do | Result |
|------|-------------|--------|
| 1 | Create `Device(id="TEST_MOTOR_1")` in DB | Device row exists |
| 2 | Create `DevicePairingSession` with random 6-digit code | Code is claimable for 5 min |
| 3 | In app: Add Device → paste code → Claim | Device appears under your user |
| 4 | Publish heartbeat, power, state to MQTT | App shows device online with live data |
| 5 | Tap ON in app, then publish ack with `command_id` | Full round trip: app → backend → MQTT → “ESP32” ack → app |

**Note:** This project uses `db` (not `database`) and `DevicePairingSession` (not `DevicePairCode`). Device state (online, last_state, last_power) is held in the backend’s MQTT layer, not in the `Device` table.
