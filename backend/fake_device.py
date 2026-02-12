import os
import json
import time
import random
import threading
from dotenv import load_dotenv
import paho.mqtt.client as mqtt

load_dotenv()

BROKER = os.getenv("MQTT_BROKER", "broker.hivemq.com")
PORT = int(os.getenv("MQTT_PORT", "1883"))
USERNAME = os.getenv("MQTT_USERNAME") or None
PASSWORD = os.getenv("MQTT_PASSWORD") or None

DEVICE_ID = "TEST_MOTOR_1"

# Topics
BASE = f"/devices/{DEVICE_ID}"
TOPIC_HEARTBEAT = f"{BASE}/heartbeat"
TOPIC_STATE = f"{BASE}/state"
TOPIC_POWER = f"{BASE}/power"
TOPIC_ACK = f"{BASE}/ack"
TOPIC_COMMAND = f"{BASE}/command"

state = "OFF"

def on_connect(client, userdata, flags, rc):
    print("Connected to broker", rc)
    client.subscribe(TOPIC_COMMAND)

def on_message(client, userdata, msg):
    global state

    print("COMMAND RECEIVED:", msg.payload.decode())

    try:
        payload = json.loads(msg.payload.decode())
        cmd_id = payload.get("command_id")
        action = payload.get("action")

        if action == "ON":
            state = "ON"
        elif action == "OFF":
            state = "OFF"

        # simulate relay delay
        time.sleep(1)

        ack = {
            "command_id": cmd_id,
            "status": "SUCCESS"
        }

        client.publish(TOPIC_ACK, json.dumps(ack))
        print("ACK SENT:", ack)

    except Exception as e:
        print("Invalid command", e)

client = mqtt.Client()

if USERNAME:
    client.username_pw_set(USERNAME, PASSWORD)

client.on_connect = on_connect
client.on_message = on_message

client.connect(BROKER, PORT, 60)

# ---- Background loops ----

def heartbeat_loop():
    while True:
        client.publish(TOPIC_HEARTBEAT, str(time.time()))
        print("heartbeat")
        time.sleep(10)

def state_loop():
    while True:
        client.publish(TOPIC_STATE, state)
        print("state:", state)
        time.sleep(15)

def power_loop():
    while True:
        power = round(random.uniform(50, 500), 1)
        client.publish(TOPIC_POWER, str(power))
        print("power:", power)
        time.sleep(5)

threading.Thread(target=heartbeat_loop, daemon=True).start()
threading.Thread(target=state_loop, daemon=True).start()
threading.Thread(target=power_loop, daemon=True).start()

client.loop_forever()
