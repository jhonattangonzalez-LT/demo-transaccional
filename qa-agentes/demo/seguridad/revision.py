#!/usr/bin/env python3
"""Revisión pasiva de seguridad de PayLink: 5 revisiones, sin ataques ni carga.

Solo hace lecturas y peticiones con claves inválidas. No crea pagos ni cambia datos.
Solo corre contra la URL autorizada en apis/paylink/config.json (autorizacion_seguridad).
Uso: python3 demo/seguridad/revision.py   -> escribe informes/seguridad/<fecha>-paylink.md
"""
import datetime
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request

RAIZ = pathlib.Path(__file__).resolve().parents[2]
CONFIG = json.loads((RAIZ / "apis" / "paylink" / "config.json").read_text(encoding="utf-8"))
BASE = (os.environ.get("PAYLINK_URL") or CONFIG["base_url"]).rstrip("/")
AUTORIZACION = CONFIG.get("autorizacion_seguridad") or {}
CLAVE_DEMO = CONFIG["api_key"]
revisiones = []

if not AUTORIZACION.get("otorgada") or BASE != CONFIG["base_url"].rstrip("/"):
    print(f"Sin autorización de seguridad para {BASE}: no se ejecuta.", flush=True)
    sys.exit(3)


def pedir(metodo, ruta, cabeceras=None, cuerpo=None):
    req = urllib.request.Request(BASE + ruta, method=metodo, data=cuerpo, headers=cabeceras or {})
    if cuerpo is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, dict(r.headers), r.read(20000).decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read(20000).decode("utf-8", "ignore")


def registrar(id_, titulo, pasa, evidencia, severidad):
    resultado = "PASA" if pasa else "HALLAZGO"
    revisiones.append({"id": id_, "titulo": titulo, "resultado": resultado, "evidencia": evidencia,
                       "severidad": "—" if pasa else severidad})
    print(f"{id_} {resultado} - {titulo} - {evidencia}", flush=True)


# SEG-01: sin clave y con clave falsa, la API no debe dejar crear pagos.
cuerpo = json.dumps({"amount": 1, "description": "seg", "returnUrl": "https://ejemplo.invalid/r"}).encode()
sin_clave = pedir("POST", "/api/v1/payments", cuerpo=cuerpo)[0]
clave_falsa = pedir("POST", "/api/v1/payments", {"X-Api-Key": "clave-falsa-123"}, cuerpo)[0]
registrar("SEG-01", "La API rechaza pagos sin clave o con clave falsa",
          sin_clave in (401, 403) and clave_falsa in (401, 403),
          f"sin clave: HTTP {sin_clave} · clave falsa: HTTP {clave_falsa}", "alta")

# SEG-02: un error no debe mostrar detalles internos (trazas de Java).
codigo, _, texto = pedir("GET", "/api/v1/payments/no-es-un-id")
traza = re.search(r"(java\.|\.java:|Exception|at [a-z]+\.[a-z.]+\()", texto)
registrar("SEG-02", "Los errores no muestran detalles internos", not traza,
          f"HTTP {codigo}" + (f"; aparece «{traza.group(0)}»" if traza else ""), "media")

# SEG-03: las rutas de administración no deben estar abiertas.
abiertas = [ruta for ruta in ("/h2-console", "/actuator/env") if pedir("GET", ruta)[0] == 200]
registrar("SEG-03", "Rutas de administración cerradas", not abiertas,
          "abiertas: " + ", ".join(abiertas) if abiertas else "/h2-console y /actuator/env no responden", "alta")

# SEG-04 y SEG-05: encabezados de seguridad y clave fuera de la página de la tienda.
_, cabeceras, pagina = pedir("GET", "/")
cabeceras = {k.lower(): v for k, v in cabeceras.items()}
faltan = [x for x in ("strict-transport-security", "x-content-type-options") if x not in cabeceras]
if "content-security-policy" not in cabeceras and "x-frame-options" not in cabeceras:
    faltan.append("content-security-policy o x-frame-options")
registrar("SEG-04", "La tienda envía encabezados de seguridad", not faltan,
          "faltan: " + ", ".join(faltan) if faltan else "presentes", "baja")
registrar("SEG-05", "La clave del comercio no aparece en la página", CLAVE_DEMO not in pagina,
          "la clave aparece en el HTML de la tienda" if CLAVE_DEMO in pagina else "no aparece", "baja")

fecha = datetime.date.today().isoformat()
salida = RAIZ / "informes" / "seguridad" / f"{fecha}-paylink.md"
salida.parent.mkdir(parents=True, exist_ok=True)
lineas = [f"# Revisión de seguridad pasiva · PayLink · {fecha}", "",
          f"URL: {BASE}",
          "Alcance: solo lecturas y peticiones con claves inválidas. No se crearon pagos ni se cargó la demo.",
          f"Autorización: {AUTORIZACION.get('por')} ({AUTORIZACION.get('fecha')}).", "",
          "| ID | Revisión | Resultado | Severidad | Evidencia |", "|---|---|---|---|---|"]
for x in revisiones:
    lineas.append(f"| {x['id']} | {x['titulo']} | {x['resultado']} | {x['severidad']} | {x['evidencia']} |")
lineas += ["", "**Decisión humana: el dueño de la demo y la persona del equipo.**", ""]
salida.write_text("\n".join(lineas), encoding="utf-8")
print(f"Informe: {salida}", flush=True)
