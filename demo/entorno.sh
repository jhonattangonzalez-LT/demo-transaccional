#!/usr/bin/env bash
# Detecta las herramientas de la demo: Java 21 (con javac), Maven, k6 y Python 3.
# Se usa con:  source demo/entorno.sh   (define JAVA_HOME, MVN, K6 y PY, o termina con un mensaje claro)

buscar() {
  # 1) en el PATH; 2) en ~/.local/bin (donde se instalan sin permisos de administrador)
  command -v "$1" 2>/dev/null || { [ -x "$HOME/.local/bin/$1" ] && echo "$HOME/.local/bin/$1"; }
}

faltan=()

if [ -z "${JAVA_HOME:-}" ]; then
  JAVAC="$(command -v javac 2>/dev/null)"
  if [ -n "$JAVAC" ]; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVAC")")")"
  elif [ -x "$HOME/.local/share/jdk-21/bin/javac" ]; then
    JAVA_HOME="$HOME/.local/share/jdk-21"
  else
    faltan+=("JDK 21 completo con javac (no solo el runtime)")
  fi
fi
export JAVA_HOME
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"

MVN="$(buscar mvn)"; [ -n "$MVN" ] || faltan+=("Maven (mvn)")
K6="$(buscar k6)";   [ -n "$K6" ]  || faltan+=("k6")
PY="$(buscar python3)"; [ -n "$PY" ] || faltan+=("Python 3")

if [ "${#faltan[@]}" -gt 0 ]; then
  echo "Faltan herramientas para la demo:" >&2
  for f in "${faltan[@]}"; do echo "  - $f" >&2; done
  echo "Revisa README.md, sección «Requisitos»." >&2
  return 1 2>/dev/null || exit 1
fi
export MVN K6 PY
