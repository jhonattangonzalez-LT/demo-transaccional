#!/usr/bin/env bash
# Coordinador de QA de PayLink (Linux/macOS/Git Bash). Uso: ./qa.sh [panel|grafo] [--tipos ...] [--carga ...]
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")" && pwd)"
if [ -x "$RAIZ/.venv/Scripts/python.exe" ]; then PY="$RAIZ/.venv/Scripts/python.exe"; else PY="$RAIZ/.venv/bin/python"; fi
if [ ! -x "$PY" ]; then
  echo "Preparando el entorno de Python por primera vez..."
  "$(command -v python3 || command -v python)" -m venv "$RAIZ/.venv"
  if [ -x "$RAIZ/.venv/Scripts/python.exe" ]; then PY="$RAIZ/.venv/Scripts/python.exe"; else PY="$RAIZ/.venv/bin/python"; fi
  "$PY" -m pip install -q --disable-pip-version-check -r "$RAIZ/orquestador/requirements.txt"
fi
exec "$PY" "$RAIZ/orquestador/qa.py" "$@"
