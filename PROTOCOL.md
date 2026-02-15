# MQTT Protocol: Backend ↔ ESP32

This document defines the exact MQTT contract between the **SmartMotor backend** and **ESP32 devices**. Use it for firmware implementation and integration testing.

---

## Overview

- **Broker**: Configured on the backend via `MQTT_BROKER`, `MQTT_PORT`, `MQTT_USERNAME`, `MQTT_PASSWORD`.
- **Topic prefix**: All device topics use `/devices/{device_id}/...` where `device_id` is a unique string (e.g. `motor-1`, `esp32-abc123`).
- **Encoding**: Payloads are UTF-8. JSON payloads use no extra whitespace.

---

## DEVICE → BACKEND

The ESP32 **publishes** to these topics. The backend **subscribes** and updates device state and command status.

### 1. Heartbeat

| Field    | Value |
| -------- | ----- |
| **Topic** | `/devices/{device_id}/heartbeat` |
| **Payload** | Any string (e.g. current timestamp). Content is ignored; receipt alone updates “last seen”. |
| **Frequency** | Every **10 seconds**. |
| **Purpose** | Backend uses this to mark the device online and to detect when it goes offline (see [Timing rules](#timing-rules)). |

**Example**

- Topic: `/devices/motor-1/heartbeat`
- Payload: `1739123456` (or any string)

---

### 2. State

| Field    | Value |
| -------- | ----- |
| **Topic** | `/devices/{device_id}/state` |
| **Payload** | Plain string: `"ON"` or `"OFF"` (no JSON wrapper). |
| **When** | Whenever the device’s logical state changes. |

**Example**

- Topic: `/devices/motor-1/state`
- Payload: `ON` or `OFF`

---

### 3. Power

| Field    | Value |
| -------- | ----- |
| **Topic** | `/devices/{device_id}/power` |
| **Payload** | Float as string (e.g. `"123.4"`). |
| **When** | Whenever a new power reading is available (e.g. from ADC or power meter). |

**Example**

- Topic: `/devices/motor-1/power`
- Payload: `123.4`

---

### 4. Command ACK

| Field    | Value |
| -------- | ----- |
| **Topic** | `/devices/{device_id}/ack` |
| **Payload** | JSON object (see below). |
| **When** | After the device has **finished** executing a command received on `/devices/{device_id}/command`. |

**Payload JSON**

```json
{
  "command_id": "<uuid>",
  "status": "SUCCESS"
}
```

- `command_id`: Exact UUID string received in the command payload. Required.
- `status`: Use `"SUCCESS"` when the command was executed successfully. (Other values are not used by the backend for ACK handling.)

**Example**

- Topic: `/devices/motor-1/ack`
- Payload: `{"command_id":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","status":"SUCCESS"}`

---

## BACKEND → DEVICE

The backend **publishes** to the command topic. The ESP32 **subscribes** and executes the action, then publishes an ACK.

### Command

| Field    | Value |
| -------- | ----- |
| **Topic** | `/devices/{device_id}/command` |
| **Payload** | JSON object (see below). |
| **When** | When the app or API requests an action (ON, OFF, UNLOCK, REPROVISION_WIFI). |

**Payload JSON**

```json
{
  "command_id": "<uuid>",
  "action": "ON"
}
```

- `command_id`: UUID string. Device must echo this in the ACK for the backend to mark the command as completed.
- `action`: One of: `"ON"` | `"OFF"` | `"UNLOCK"` | `"REPROVISION_WIFI"`.

**Examples**

- Topic: `/devices/motor-1/command`
- Payload (turn on): `{"command_id":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","action":"ON"}`
- Payload (turn off): `{"command_id":"b2c3d4e5-f6a7-8901-bcde-f12345678901","action":"OFF"}`

---

## Timing rules

| Rule | Value | Description |
| ---- | ----- | ----------- |
| **Heartbeat interval** | **10 s** | ESP32 must publish to `/devices/{device_id}/heartbeat` at least every 10 seconds so the backend considers the device online. |
| **Command timeout** | **10 s** | If the backend does not receive an ACK for a command within 10 seconds of publishing it, the command is marked **FAILED**. The device should ACK as soon as the action is done. |
| **Offline threshold** | **30 s** | If no heartbeat (or other message that updates “last seen”) is received for **30 seconds**, the backend marks the device **offline**. |

**Summary for firmware**

1. Publish heartbeat every 10 s.
2. Publish state when state changes; publish power when you have a new reading.
3. Subscribe to `/devices/{device_id}/command`; on each message, parse JSON, run the action, then publish to `/devices/{device_id}/ack` with the same `command_id` and `"status":"SUCCESS"` within 10 s.

---

## Quick reference

| Direction   | Topic suffix | Payload |
| ---------- | ------------ | ------- |
| Device → Backend | `/devices/{id}/heartbeat` | any string |
| Device → Backend | `/devices/{id}/state`     | `ON` or `OFF` |
| Device → Backend | `/devices/{id}/power`     | float string, e.g. `123.4` |
| Device → Backend | `/devices/{id}/ack`       | `{"command_id":"<uuid>","status":"SUCCESS"}` |
| Backend → Device | `/devices/{id}/command`   | `{"command_id":"<uuid>","action":"ON"\|"OFF"\|"UNLOCK"\|"REPROVISION_WIFI"}` |
