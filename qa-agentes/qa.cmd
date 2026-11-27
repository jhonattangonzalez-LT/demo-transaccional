@echo off
rem Coordinador de QA de PayLink (Windows). Uso: qa.cmd [panel|grafo] [--tipos api,front,carga,seguridad] [--carga corta|carga|estres] ...
setlocal
set "RAIZ=%~dp0"
if not exist "%RAIZ%.venv\Scripts\python.exe" (
  echo Preparando el entorno de Python por primera vez...
  python -m venv "%RAIZ%.venv" || exit /b 1
  "%RAIZ%.venv\Scripts\python.exe" -m pip install -q --disable-pip-version-check -r "%RAIZ%orquestador\requirements.txt" || exit /b 1
)
"%RAIZ%.venv\Scripts\python.exe" "%RAIZ%orquestador\qa.py" %*
