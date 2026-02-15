# SmartMotorApp

Android + FastAPI + MQTT personal IoT project.

- `app/`: Android app (Jetpack Compose) that shows ESP32 devices, status, power and lets you send commands.
- `backend/`: FastAPI backend that connects to an MQTT broker, tracks device state in memory + SQLite (`smartmotor.db`), and exposes a REST API for the app.
- No Tuya SDK or Tuya Cloud is used anymore.

Backend is designed to run from the `backend/` folder (e.g. on Render) with:

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```
