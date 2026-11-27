#!/usr/bin/env python3
"""Sirve la presentación de la demo y lanza sus casos con un botón.

Uso:  python3 demo/servidor.py     y abrir  http://127.0.0.1:8791

Solo escucha en 127.0.0.1. Solo ejecuta cuatro comandos fijos (uno por caso).
El navegador no envía comandos ni rutas: solo pide el nombre de un caso.
"""
import json
import os
import pathlib
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

RAIZ = pathlib.Path(__file__).resolve().parent.parent
ESTADO = RAIZ / "demo" / "estado"
ESTADO.mkdir(parents=True, exist_ok=True)
PORT = 8791
import shutil

def _buscar(nombre):
    """Busca la herramienta en el PATH y, si no está, en ~/.local/bin."""
    return shutil.which(nombre) or (str(pathlib.Path.home() / ".local/bin" / nombre)
                                    if (pathlib.Path.home() / ".local/bin" / nombre).exists() else nombre)


MVN = _buscar("mvn")
K6 = _buscar("k6")
_javac = shutil.which("javac")
JDK = str(pathlib.Path(os.path.realpath(_javac)).parents[1]) if _javac else str(pathlib.Path.home() / ".local/share/jdk-21")
lock = threading.Lock()
procesos = {}


def entorno():
    env = dict(os.environ)
    if "JAVA_HOME" not in env:
        env["JAVA_HOME"] = JDK
    env["PATH"] = f"{env['JAVA_HOME']}/bin:{env.get('PATH', '')}"
    env.setdefault("DISPLAY", ":0")
    return env


def comando(caso):
    """Cuatro comandos fijos, uno por caso de la presentación."""
    serenity = RAIZ / "serenity-tienda"
    if caso == "flujo":
        return [MVN, "-B", "test", "-Dventana=false", "-Ddemo=true",
                "-Dtest=PagoTiendaTest#pagoAprobado"], serenity, entorno()
    if caso == "datos":
        return [MVN, "-B", "test", "-Dventana=false", "-Ddemo=true",
                "-Dtest=DatosFormularioTest#documentoEnBancoEquivocado"], serenity, entorno()
    if caso == "carga":
        env = entorno()
        env["TIPO"] = "carga"
        return [K6, "run", "k6/paylink.js"], RAIZ, env
    if caso == "seguridad":
        return [sys.executable, "demo/seguridad/revision.py"], RAIZ, entorno()
    return None


def lanzar(caso):
    datos = comando(caso)
    if datos is None:
        return False, "caso desconocido"
    cmd, cwd, env = datos
    with lock:
        if caso in procesos and procesos[caso].poll() is None:
            return False, "ya está corriendo"
        (ESTADO / f"{caso}.estado").write_text("corriendo")
        log = open(ESTADO / f"{caso}.log", "w")
        p = subprocess.Popen(cmd, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT,
                             start_new_session=True)
        procesos[caso] = p

    def vigilar():
        codigo = p.wait()
        log.close()
        (ESTADO / f"{caso}.estado").write_text("ok" if codigo == 0 else "fallo")

    threading.Thread(target=vigilar, daemon=True).start()
    return True, "iniciado"


def estado(caso):
    archivo_estado = ESTADO / f"{caso}.estado"
    archivo_log = ESTADO / f"{caso}.log"
    e = archivo_estado.read_text().strip() if archivo_estado.exists() else "sin_correr"
    lineas = []
    if archivo_log.exists():
        # Se quitan los códigos de color de Maven y se muestran las últimas líneas útiles.
        texto = archivo_log.read_text(errors="ignore")
        lineas = [l for l in texto.splitlines() if l.strip()][-14:]
    return {"estado": e, "lineas": lineas}


class Handler(BaseHTTPRequestHandler):
    def _json(self, datos, codigo=200):
        cuerpo = json.dumps(datos, ensure_ascii=False).encode()
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(cuerpo)))
        self.end_headers()
        self.wfile.write(cuerpo)

    def _archivo(self, ruta_archivo, tipo):
        if not ruta_archivo.is_file():
            self._json({"error": "no existe"}, 404)
            return
        cuerpo = ruta_archivo.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(cuerpo)))
        self.end_headers()
        self.wfile.write(cuerpo)

    def do_GET(self):
        ruta = self.path.split("?")[0]
        if ruta == "/":
            self._archivo(RAIZ / "demo" / "presentacion.html", "text/html; charset=utf-8")
        elif ruta.startswith("/api/estado/"):
            self._json(estado(ruta.rsplit("/", 1)[-1]))
        elif ruta.startswith("/informes/"):
            # Solo lectura de los informes del proyecto.
            destino = (RAIZ / ruta.lstrip("/")).resolve()
            if RAIZ in destino.parents and "informes" in destino.parts:
                tipo = "text/html; charset=utf-8" if destino.suffix == ".html" else "text/plain; charset=utf-8"
                self._archivo(destino, tipo)
            else:
                self._json({"error": "no permitido"}, 403)
        elif ruta.startswith("/serenity/"):
            destino = (RAIZ / "serenity-tienda/target/site/serenity" / ruta[len("/serenity/"):]).resolve()
            base = (RAIZ / "serenity-tienda/target/site/serenity").resolve()
            if base in destino.parents or destino == base:
                tipo = "text/html; charset=utf-8" if destino.suffix == ".html" else "application/octet-stream"
                self._archivo(destino, tipo)
            else:
                self._json({"error": "no permitido"}, 403)
        else:
            self._json({"error": "no existe"}, 404)

    def do_POST(self):
        ruta = self.path.split("?")[0]
        if ruta.startswith("/api/ejecutar/"):
            ok, msg = lanzar(ruta.rsplit("/", 1)[-1])
            self._json({"ok": ok, "mensaje": msg}, 200 if ok else 409)
        else:
            self._json({"error": "no existe"}, 404)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    servidor = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Presentación de la demo: http://127.0.0.1:{PORT}  (Ctrl+C para detener)", flush=True)
    servidor.serve_forever()
