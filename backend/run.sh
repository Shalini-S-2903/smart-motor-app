#!/bin/sh
# Run FastAPI server. Uses venv if present so "uvicorn" is available.
cd "$(dirname "$0")"
if [ -d .venv ]; then
  . .venv/bin/activate
fi
exec uvicorn main:app --reload --host 0.0.0.0 --port 8000
