"""Panel web local para ver la ejecución en vivo (solo escucha en 127.0.0.1).

GET  /                     panel
GET  /eventos              eventos en vivo (Server-Sent Events) de la ejecución actual
GET  /api/estado           si hay una ejecución en curso
POST /api/ejecutar         lanza el grafo: {"tipos": [...], "perfil_carga": "corta", "ventana": true, "url": "https://..."}
GET  /ver/<ruta>           informes generados (solo informes/ y el informe de Serenity)
"""
import json
import mimetypes
import pathlib
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import grafo
from ejecutores import RAIZ
from eventos import bus

AQUI = pathlib.Path(__file__).resolve().parent
PERMITIDAS = [RAIZ / "informes", RAIZ / "serenity-tienda" / "target" / "site" / "serenity"]
PERFILES = {"smoke", "corta", "carga", "estres"}

_ejecucion = {"hilo": None}
_candado = threading.Lock()


def en_curso():
    hilo = _ejecucion["hilo"]
    return grafo.en_curso() or (hilo is not None and hilo.is_alive())


def lanzar(tipos, perfil_carga="corta", ventana=True, url=None):
    """Inicia el grafo en segundo plano. Devuelve False si ya hay una ejecución en curso."""
    with _candado:
        if en_curso():
            return False
        hilo = threading.Thread(target=grafo.ejecutar, args=(tipos, perfil_carga, ventana, url), daemon=True)
        _ejecucion["hilo"] = hilo
        hilo.start()
        return True


class Manejador(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _responder(self, codigo, cuerpo, tipo="application/json; charset=utf-8"):
        datos = cuerpo if isinstance(cuerpo, bytes) else json.dumps(cuerpo, ensure_ascii=False).encode("utf-8")
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(datos)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(datos)

    def do_GET(self):
        ruta = self.path.split("?")[0]
        if ruta == "/":
            self._responder(200, (AQUI / "panel.html").read_bytes(), "text/html; charset=utf-8")
        elif ruta == "/quind.png":
            self._responder(200, (AQUI / "quind.png").read_bytes(), "image/png")
        elif ruta == "/api/estado":
            self._responder(200, {"en_curso": en_curso(), "tipos": grafo.TIPOS, "nombres": grafo.NOMBRES,
                                  "url": grafo.ejecutores.URL_DEMO})
        elif ruta == "/eventos":
            self._sse()
        elif ruta.startswith("/ver/"):
            self._archivo(ruta[len("/ver/"):])
        else:
            self._responder(404, {"error": "no existe"})

    def _origen_valido(self):
        """Solo acepta POST del propio panel (evita que otra página web lance pruebas en este equipo)."""
        origen = self.headers.get("Origin")
        puerto = self.server.server_address[1]
        es_json = (self.headers.get("Content-Type") or "").startswith("application/json")
        return es_json and origen in (None, f"http://127.0.0.1:{puerto}", f"http://localhost:{puerto}")

    def do_POST(self):
        ruta = self.path.split("?")[0]
        if not self._origen_valido():
            self._responder(403, {"error": "origen no permitido"})
            return
        if ruta == "/api/apagar":
            # Lo usa un nuevo `qa.cmd` para reemplazar a un panel anterior que quedó abierto.
            if en_curso():
                self._responder(409, {"error": "hay una ejecución en curso"})
                return
            self._responder(200, {"ok": True})
            threading.Thread(target=_apagar_proceso, daemon=True).start()
            return
        if ruta != "/api/ejecutar":
            self._responder(404, {"error": "no existe"})
            return
        largo = int(self.headers.get("Content-Length") or 0)
        try:
            pedido = json.loads(self.rfile.read(largo) or b"{}")
        except ValueError:
            self._responder(400, {"error": "JSON inválido"})
            return
        tipos = [t for t in pedido.get("tipos", grafo.TIPOS) if t in grafo.TIPOS]
        perfil = pedido.get("perfil_carga", "corta")
        url = str(pedido.get("url") or "").strip().rstrip("/") or None
        if not tipos or perfil not in PERFILES:
            self._responder(400, {"error": "tipos o perfil de carga inválidos"})
            return
        if url and (not url.startswith(("http://", "https://")) or " " in url):
            self._responder(400, {"error": "la URL debe empezar por http:// o https://"})
            return
        if lanzar(tipos, perfil, bool(pedido.get("ventana", True)), url):
            self._responder(202, {"ok": True})
        else:
            self._responder(409, {"error": "ya hay una ejecución en curso"})

    def _sse(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "keep-alive")
        self.end_headers()
        ultimo = self.headers.get("Last-Event-ID")
        seq = bus.inicio_actual
        # Si el navegador se reconecta a este mismo panel, sigue desde donde iba; si viene de otro panel, empieza de cero.
        if ultimo and ultimo.isdigit() and bus.inicio_actual <= int(ultimo) < bus.total():
            seq = int(ultimo) + 1
        try:
            while True:
                eventos = bus.desde(seq, timeout=15)
                if not eventos:
                    self.wfile.write(b": latido\n\n")
                for ev in eventos:
                    self.wfile.write(f"id: {ev['seq']}\ndata: {json.dumps(ev, ensure_ascii=False)}\n\n".encode("utf-8"))
                    seq = ev["seq"] + 1
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
            return

    def _archivo(self, relativa):
        from urllib.parse import unquote
        destino = (RAIZ / unquote(relativa)).resolve()
        if not any(destino == base or base in destino.parents for base in PERMITIDAS) or not destino.is_file():
            self._responder(404, {"error": "no disponible"})
            return
        tipo = mimetypes.guess_type(destino.name)[0] or "application/octet-stream"
        if destino.suffix in (".md", ".log", ".jsonl"):
            tipo = "text/plain"
        if tipo.startswith("text/") or tipo in ("application/javascript", "application/json"):
            tipo += "; charset=utf-8"
        self._responder(200, destino.read_bytes(), tipo)

    def log_message(self, *args):
        pass


class Servidor(ThreadingHTTPServer):
    daemon_threads = True
    # En Windows, SO_REUSEADDR deja que dos procesos escuchen en el mismo puerto y el navegador
    # termina hablando con el panel viejo. Sin reutilización, el segundo arranque falla y se detecta.
    allow_reuse_address = False

    def handle_error(self, request, client_address):
        # Un navegador que cierra la pestaña corta la conexión: no es un error que deba ensuciar la consola.
        import sys
        if isinstance(sys.exc_info()[1], (ConnectionAbortedError, ConnectionResetError, BrokenPipeError)):
            return
        super().handle_error(request, client_address)


def _apagar_proceso():
    """Cierra este proceso (y su ventana de consola) para liberar el puerto al panel nuevo."""
    import os
    import time
    time.sleep(0.5)
    print("\nOtro qa.cmd tomó el panel; esta ventana se cierra.", flush=True)
    os._exit(0)


def reemplazar_panel_anterior(puerto):
    """Si un panel anterior ocupa el puerto: lo cierra si está libre, o devuelve un mensaje si está ejecutando."""
    import time
    import urllib.request
    base = f"http://127.0.0.1:{puerto}"
    try:
        with urllib.request.urlopen(base + "/api/estado", timeout=3) as r:
            if json.loads(r.read()).get("en_curso"):
                return f"Ya hay una ejecución en curso. Mírala en {base} o espera a que termine."
        req = urllib.request.Request(base + "/api/apagar", data=b"{}", method="POST",
                                     headers={"Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=3).read()
    except Exception:
        return f"El puerto {puerto} está ocupado por otro programa. Usa --puerto con otro número."
    for _ in range(20):
        time.sleep(0.25)
        try:
            return servir(puerto)
        except OSError:
            continue
    return f"No se pudo liberar el puerto {puerto}."


def servir(puerto=8792):
    servidor = Servidor(("127.0.0.1", puerto), Manejador)
    servidor.daemon_threads = True
    hilo = threading.Thread(target=servidor.serve_forever, daemon=True)
    hilo.start()
    return servidor
