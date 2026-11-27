#!/usr/bin/env bash
# Demo para presentación: un caso por tipo de prueba.
#  1. Funcional de flujo (front): pago aprobado, con Chrome visible y un rótulo por paso.
#  2. Funcional de ingreso de datos (front): documento en banco equivocado, con Chrome visible.
#  3. No funcional (carga): 10 usuarios durante 3 minutos con k6 (sin navegador).
#  4. Seguridad: revisión pasiva de la API y la tienda (sin ataques ni carga).
# Al terminar abre en Chrome los informes de carga y de Serenity.
# Uso: ./demo/ejecutar_demo.sh
set -uo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
source "$RAIZ/demo/entorno.sh" || exit 1
export DISPLAY="${DISPLAY:-:0}"
FECHA="$(date +%F)"
fallos=0

# Presentación de la demo: se abre en Chrome al empezar.
(google-chrome --new-window "file://$RAIZ/demo/presentacion.html" >/dev/null 2>&1 &)
sleep 3

echo "== 1/4 Funcional · flujo de pago (Chrome visible)"
(cd "$RAIZ/serenity-tienda" && "$MVN" -B test -Dventana=false -Ddemo=true -Dtest='PagoTiendaTest#pagoAprobado') || fallos=$((fallos+1))

echo "== 2/4 Funcional · ingreso de datos (Chrome visible)"
(cd "$RAIZ/serenity-tienda" && "$MVN" -B test -Dventana=false -Ddemo=true -Dtest='DatosFormularioTest#documentoEnBancoEquivocado') || fallos=$((fallos+1))

echo "== 3/4 No funcional · carga de la API (k6, 10 usuarios, 3 min)"
(cd "$RAIZ" && TIPO=carga "$K6" run k6/paylink.js) || fallos=$((fallos+1))

echo "== 4/4 Seguridad · revisión pasiva de la API y la tienda"
(cd "$RAIZ" && "$PY" demo/seguridad/revision.py) || fallos=$((fallos+1))

echo
echo "Informes:"
echo "  Carga:     $RAIZ/informes/carga/${FECHA}-paylink-carga.html"
echo "  Serenity:  $RAIZ/serenity-tienda/target/site/serenity/index.html"
echo "  Seguridad: $RAIZ/informes/seguridad/${FECHA}-paylink.md"

for f in "$RAIZ/informes/carga/${FECHA}-paylink-carga.html" "$RAIZ/serenity-tienda/target/site/serenity/index.html"; do
  [ -f "$f" ] && (google-chrome --new-window "file://$f" >/dev/null 2>&1 &)
done

echo
if [ "$fallos" -eq 0 ]; then echo "Demo terminada: los 4 casos corrieron."; else echo "Demo terminada con $fallos caso(s) que fallaron. Revisa la salida de arriba."; fi
exit "$fallos"
