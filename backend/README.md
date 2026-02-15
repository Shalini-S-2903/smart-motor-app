# Tuya Cloud API Proxy (FastAPI)

HTTP-only proxy to Tuya Cloud OpenAPI. No Tuya SDK. Works with a **free Tuya developer account**.

## Setup

1. Create a project at [Tuya IoT Platform](https://iot.tuya.com) → Cloud → Development → API.
2. Copy **Access ID** and **Access Secret**.
3. Copy `.env.example` to `.env` and set:

```bash
cp .env.example .env
# Edit .env:
# TUYA_ACCESS_ID=...
# TUYA_ACCESS_SECRET=...
# TUYA_REGION=eu   # or in, us, cn
```

4. Install and run (must use `host=0.0.0.0` so Android emulator can reach the server).

   **Option A – virtual environment (recommended on macOS):**
   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

   **Option B – run script (after `pip install -r requirements.txt` inside `.venv`):**
   ```bash
   chmod +x run.sh
   ./run.sh
   ```

   **Option C – without venv (if uvicorn is on PATH):**
   ```bash
   pip install -r requirements.txt   # may require --user or a venv
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

   Or with env from file:

```bash
export $(grep -v '^#' .env | xargs)
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/devices` | List devices |
| POST | `/devices/{device_id}/on` | Turn device ON |
| POST | `/devices/{device_id}/off` | Turn device OFF |
| GET | `/health` | Health check |

## Android

Point the app's **backend base URL** to this server (e.g. `http://10.0.2.2:8000` for emulator, or your machine's LAN IP).

## Debugging Tuya / 502 errors

- **Logging**: All Tuya API calls are logged (request URL, redacted headers, response status and body). Watch console output when calling `/devices` or `/health`.
- **GET /health**: Now checks Tuya token retrieval. If token fails, returns **503** with Tuya’s exact error message (no generic 502).
- **Other endpoints**: On Tuya failure, the backend returns **502** with Tuya’s message in `detail` (e.g. `[code] msg`).

**Example success logs:**
```
INFO:tuya_client:Tuya token request url=https://openapi.tuyaeu.com/v1.0/token?grant_type=1 headers=...
INFO:tuya_client:Tuya token response status=200 body={"success":true,"result":{"access_token":"...","expire":7200,"uid":"..."}}
INFO:tuya_client:Tuya token ok uid=... expire=7200
```

**Example failure (wrong credentials or region):**
```
INFO:tuya_client:Tuya token request url=https://openapi.tuyaeu.com/v1.0/token?grant_type=1 headers=...
INFO:tuya_client:Tuya token response status=200 body={"success":false,"msg":"sign invalid","code":...}
```
Then `/health` returns 503 with detail like `sign invalid` or `Invalid client_id`.

**Most likely Tuya error causes:**
1. **Wrong credentials** – Use **Cloud** project (IoT Platform → Cloud → API), not App SDK keys.
2. **Wrong region** – `TUYA_REGION` must match the project (eu/in/us/cn).
3. **Sign invalid** – Check system time (timestamp in ms); ensure no extra spaces in env vars.
4. **No devices** – Token may succeed but `/v1.0/users/{uid}/devices` returns empty if no devices are linked to that cloud user.
