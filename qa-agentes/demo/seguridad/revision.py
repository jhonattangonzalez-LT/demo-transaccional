#!/usr/bin/env python3
"""Revisiones pasivas de seguridad sobre la demo PayLink (sin ataques ni carga).

Solo hace lecturas y peticiones con claves inválidas. No crea pagos ni cambia datos.
Autorizado por el dueño de la demo (dueño de la demo).
Uso: python3 demo/seguridad/revision.py   -> escribe informes/seguridad/<fecha>-paylink.md
"""
import datetime
import json
import pathlib
import re
import urllib.error
import urllib.request

RAIZ = pathlib.Path(__file__).resolve().parents[2]
BASE = "https://demo-transaccional.vercel.app"
CLAVE_DEMO = "demo-api-key-123"
hallazgos = []


def pedir(metodo, ruta, cabeceras=None, cuerpo=None):
    req = urllib.request.Request(BASE + ruta, method=metodo, data=cuerpo, headers=cabeceras or {})
    if cuerpo is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, dict(r.headers), r.read(20000).decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read(20000).decode("utf-8", "ignore")


def registrar(id_, titulo, resultado, evidencia, severidad="—"):
    hallazgos.append({"id": id_, "titulo": titulo, "resultado": resultado, "evidencia": evidencia, "severidad": severidad})
    print(f"{id_} {resultado} - {titulo} - {evidencia}", flush=True)


body = json.dumps({"amount": 1, "description": "seg", "returnUrl": "https://ejemplo.invalid/r"}).encode()

c, _, _ = pedir("POST", "/api/v1/payments", cuerpo=body)
registrar("SEG-01", "Crear pago sin X-Api-Key", "PASA" if c in (401, 403) else "HALLAZGO",
          f"HTTP {c}", "alta" if c not in (401, 403) else "—")

c, _, _ = pedir("POST", "/api/v1/payments", {"X-Api-Key": "clave-falsa-123"}, body)
registrar("SEG-02", "Crear pago con clave inválida", "PASA" if c in (401, 403) else "HALLAZGO",
          f"HTTP {c}", "alta" if c not in (401, 403) else "—")

c, _, t = pedir("GET", "/api/v1/payments/00000000-0000-0000-0000-000000000000")
registrar("SEG-03", "Consulta de pago inexistente sin clave", "PASA" if c in (401, 403, 404) else "REVISAR",
          f"HTTP {c}", "media" if c == 200 else "—")

c, _, t = pedir("GET", "/api/v1/payments/no-es-un-id")
filtra = re.search(r"(java\.|\.java:|Exception|at [a-z]+\.[a-z.]+\()", t)
registrar("SEG-04", "Errores sin trazas internas", "HALLAZGO" if filtra else "PASA",
          f"HTTP {c}" + (f"; aparece «{filtra.group(0)}»" if filtra else ""), "media" if filtra else "—")

for ruta, id_, nombre in [("/h2-console", "SEG-05a", "consola H2"), ("/actuator/env", "SEG-05b", "actuator/env")]:
    c, _, _ = pedir("GET", ruta)
    registrar(id_, f"Ruta {nombre} no expuesta", "HALLAZGO" if c == 200 else "PASA", f"HTTP {c}",
              "alta" if c == 200 else "—")

c, h, _ = pedir("GET", "/")
h = {k.lower(): v for k, v in h.items()}
faltan = [x for x in ["strict-transport-security", "x-content-type-options"] if x not in h]
if "content-security-policy" not in h and "x-frame-options" not in h:
    faltan.append("content-security-policy o x-frame-options")
registrar("SEG-06", "Encabezados de seguridad en la tienda", "PASA" if not faltan else "HALLAZGO",
          "faltan: " + ", ".join(faltan) if faltan else "presentes", "baja" if faltan else "—")

pagina = pedir("GET", "/")[2]
registrar("SEG-07", "Clave del comercio fuera de la página", "HALLAZGO" if CLAVE_DEMO in pagina else "PASA",
          "la clave aparece en el HTML de la tienda" if CLAVE_DEMO in pagina else "no aparece",
          "baja" if CLAVE_DEMO in pagina else "—")

registrar("SEG-08", "Acceso entre comercios (OWASP API1)", "NO MEDIDO",
          "la demo tiene un solo comercio; no hay otro dueño de pagos para probar", "—")

fecha = datetime.date.today().isoformat()
salida = RAIZ / "informes" / "seguridad" / f"{fecha}-paylink.md"
salida.parent.mkdir(parents=True, exist_ok=True)
lineas = [f"# Revisión de seguridad pasiva · PayLink (demo) · {fecha}", "",
          "Alcance: solo lecturas y peticiones con claves inválidas. No se crearon pagos ni se cargó la demo.",
          "Autorización: dada por el dueño de la demo (dueño de la demo).", "",
          "| ID | Revisión | Resultado | Severidad | Evidencia |", "|---|---|---|---|---|"]
for x in hallazgos:
    lineas.append(f"| {x['id']} | {x['titulo']} | {x['resultado']} | {x['severidad']} | {x['evidencia']} |")
lineas += ["", "**Decisión humana: el dueño de la demo y la persona del equipo.**", ""]
salida.write_text("\n".join(lineas), encoding="utf-8")
print(f"Informe: {salida}", flush=True)
