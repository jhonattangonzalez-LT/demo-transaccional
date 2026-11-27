"""Ejecutores de cada tipo de prueba. Cada uno corre la herramienta real (k6, Maven/Serenity, Python),
publica en vivo cada paso en el bus de eventos y devuelve un resultado resumido.

Resultado: {"estado": PASA|FALLA|HALLAZGOS|OMITIDO|ERROR, "resumen": str, "metricas": {...}, "informes": [...]}
"""
import datetime
import glob
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

from eventos import bus

RAIZ = pathlib.Path(__file__).resolve().parents[1]
CONFIG = json.loads((RAIZ / "apis" / "paylink" / "config.json").read_text(encoding="utf-8"))
URL_DEMO = CONFIG["base_url"].rstrip("/")
BASE = URL_DEMO  # URL contra la que se prueba; se cambia con usar_url() (opción --url o campo URL del panel)


def usar_url(url=None):
    """Fija la URL de esta ejecución. Debe exponer la misma API de PayLink (mismos endpoints y datos de prueba)."""
    global BASE
    BASE = (url or URL_DEMO).strip().rstrip("/")
    return BASE


# --------------------------------------------------------------------------- utilidades

def herramienta(nombre):
    """Ubica k6 o mvn: variable de entorno (K6 / MVN), PATH, o rutas de instalación conocidas."""
    explicita = os.environ.get(nombre.upper())
    if explicita and pathlib.Path(explicita).exists():
        return explicita
    encontrada = shutil.which(nombre)
    if encontrada:
        return encontrada
    casa = pathlib.Path.home()
    candidatos = {
        "k6": [r"C:\Program Files\k6\k6.exe", str(casa / ".local/bin/k6")],
        "mvn": sorted(glob.glob(str(casa / "tools" / "apache-maven-*" / "bin" / "mvn.cmd")), reverse=True)
               + [str(casa / ".local/bin/mvn")],
    }.get(nombre, [])
    for c in candidatos:
        if pathlib.Path(c).exists():
            return c
    return None


def correr(cmd, cwd, env=None, al_leer_linea=None, log=None, timeout=1800):
    """Ejecuta un proceso, entrega cada línea de salida a `al_leer_linea` y la guarda en `log`. Devuelve el código."""
    entorno = dict(os.environ, PYTHONIOENCODING="utf-8", **(env or {}))
    p = subprocess.Popen(cmd, cwd=cwd, env=entorno, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         text=True, encoding="utf-8", errors="replace", bufsize=1)
    archivo = open(log, "w", encoding="utf-8") if log else None
    limite = time.time() + timeout
    try:
        for linea in p.stdout:
            if archivo:
                archivo.write(linea)
            if al_leer_linea:
                al_leer_linea(linea.rstrip("\n"))
            if time.time() > limite:
                p.kill()
                break
        return p.wait()
    finally:
        if archivo:
            archivo.close()


class Cola:
    """Lee en segundo plano las líneas nuevas de un archivo mientras otro proceso lo escribe (como `tail -f`)."""

    def __init__(self, ruta, al_leer_linea):
        self.ruta = pathlib.Path(ruta)
        self.al_leer_linea = al_leer_linea
        self._parar = threading.Event()
        self._hilo = threading.Thread(target=self._leer, daemon=True)

    def __enter__(self):
        self._hilo.start()
        return self

    def __exit__(self, *exc):
        self._parar.set()
        self._hilo.join(timeout=5)

    def _leer(self):
        posicion, resto = 0, ""
        while True:
            parar = self._parar.is_set()
            if self.ruta.exists():
                with open(self.ruta, "r", encoding="utf-8", errors="replace") as f:
                    f.seek(posicion)
                    bloque = f.read()
                    posicion = f.tell()
                lineas = (resto + bloque).split("\n")
                resto = lineas.pop()
                for linea in lineas:
                    if linea.strip():
                        self.al_leer_linea(linea)
            if parar:
                if resto.strip():
                    self.al_leer_linea(resto)
                return
            time.sleep(0.3)


def pedir(ruta, timeout=60):
    """Devuelve el código HTTP, o el texto del error si no hubo respuesta (DNS, conexión, certificado...)."""
    req = urllib.request.Request(BASE + ruta, headers={"User-Agent": "paylink-qa-orquestador"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception as e:
        return f"sin respuesta: {getattr(e, 'reason', e)}"


def percentil(valores, p):
    if not valores:
        return 0.0
    ordenados = sorted(valores)
    k = max(0, min(len(ordenados) - 1, round(p / 100 * len(ordenados) + 0.5) - 1))
    return ordenados[k]


# --------------------------------------------------------------------------- nodos

def calentar(carpeta):
    """Comprueba que la URL responde con la API de PayLink. En Vercel la despierta antes de medir (evita P95 falsos)."""
    bus.emitir("calentar", "paso", f"Comprobando {BASE} (si estaba dormida puede tardar ~15 s)", "INFO")
    inicio = time.time()
    while time.time() - inicio < 60:
        codigo = pedir("/api/v1/banks")
        if codigo == 200:
            segundos = time.time() - inicio
            bus.emitir("calentar", "paso", f"Responde con la API de PayLink (en {segundos:.1f} s)", "PASA")
            return {"estado": "PASA", "resumen": f"URL disponible en {segundos:.1f} s", "metricas": {}, "informes": []}
        if isinstance(codigo, int) and 400 <= codigo < 500 and codigo != 429:
            bus.emitir("calentar", "paso", f"La URL responde, pero no tiene la API de PayLink (/api/v1/banks da HTTP {codigo})", "FALLA")
            return {"estado": "FALLA", "resumen": "la URL no tiene la API de PayLink", "metricas": {}, "informes": []}
        bus.emitir("calentar", "log", f"Aún no responde ({codigo}); reintentando…")
        time.sleep(3)
    bus.emitir("calentar", "paso", f"No respondió en 1 minuto (último: {codigo})", "FALLA")
    return {"estado": "FALLA", "resumen": "URL no disponible", "metricas": {}, "informes": []}


def _k6(tipo_nodo, perfil, carpeta, en_vivo):
    k6 = herramienta("k6")
    if not k6:
        bus.emitir(tipo_nodo, "paso", "k6 no está instalado (ver README, sección Requisitos)", "FALLA")
        return None, None
    (RAIZ / "informes" / "carga").mkdir(parents=True, exist_ok=True)
    salida_json = carpeta / f"k6-{tipo_nodo}.jsonl"
    informe = {}

    def linea_k6(linea):
        m = re.search(r"Informe: (\S+)\.html", linea)
        if m:
            informe["base"] = m.group(1)

    with Cola(salida_json, en_vivo):
        codigo = correr([k6, "run", "--no-color", "-q", "--out", f"json={salida_json}", "k6/paylink.js"],
                        cwd=RAIZ, env={"TIPO": perfil, "BASE_URL": BASE}, al_leer_linea=linea_k6, log=carpeta / f"k6-{tipo_nodo}.log")
    resumen = None
    if "base" in informe:
        ruta_json = RAIZ / f"{informe['base']}.json"
        if ruta_json.exists():
            resumen = json.loads(ruta_json.read_text(encoding="utf-8"))
        bus.emitir(tipo_nodo, "informe", "Informe k6", ruta=f"{informe['base']}.html")
    return codigo, (resumen, informe.get("base"))


def _metricas_k6(resumen):
    m = (resumen or {}).get("metrics", {})
    v = lambda n, c: (m.get(n, {}).get("values", {}) or {}).get(c)
    return {
        "peticiones": v("http_reqs", "count") or 0,
        "p95_ms": round(v("http_req_duration", "p(95)") or 0),
        "promedio_ms": round(v("http_req_duration", "avg") or 0),
        "errores_pct": round((v("http_req_failed", "rate") or 0) * 100, 2),
        "checks_pasan": v("checks", "passes") or 0,
        "checks_fallan": v("checks", "fails") or 0,
    }


def api_funcional(carpeta):
    """Prueba funcional de la API: flujo completo (aprobado + rechazado) con k6 en modo smoke."""
    bus.emitir("api", "paso", "6 validaciones de un pago completo: crear, iniciar, iniciar sesión en el banco, aprobar, saldos y rechazo", "INFO")

    def en_vivo(linea):
        try:
            ev = json.loads(linea)
        except ValueError:
            return
        if ev.get("type") == "Point" and ev.get("metric") == "checks":
            nombre = ev["data"]["tags"].get("check", "")
            bus.emitir("api", "paso", nombre, "PASA" if ev["data"]["value"] == 1 else "FALLA")

    codigo, (resumen, base) = _k6("api", "smoke", carpeta, en_vivo) if herramienta("k6") else (None, (None, None))
    if codigo is None:
        return {"estado": "ERROR", "resumen": "k6 no disponible", "metricas": {}, "informes": []}
    met = _metricas_k6(resumen)
    estado = "PASA" if codigo == 0 and met["checks_fallan"] == 0 else "FALLA"
    motivo = "" if estado == "PASA" else (" (umbral de tiempo superado)" if codigo == 99 else f" (código {codigo})")
    return {
        "estado": estado,
        "resumen": f"{met['checks_pasan']} validaciones pasan, {met['checks_fallan']} fallan; P95 {met['p95_ms']} ms{motivo}",
        "metricas": met,
        "informes": [f"{base}.html"] if base else [],
    }


def carga(carpeta, perfil="corta"):
    """Prueba de carga con k6: publica cada segundo usuarios activos, req/s, P95 y errores."""
    descripcion = {"corta": "5 usuarios ~45 s", "carga": "10 usuarios ~3 min", "estres": "hasta 50 usuarios ~3,5 min",
                   "smoke": "1 usuario, 1 iteración"}.get(perfil, perfil)
    bus.emitir("carga", "paso", f"Varios usuarios pagando a la vez ({descripcion}); "
                                f"pasa si el 95 % responde en menos de {CONFIG['umbral_p95_ms']} ms y casi sin errores", "INFO")
    estado_vivo = {"vus": 0, "duraciones": [], "peticiones": 0, "fallidas": 0, "ventana": 0, "ultimo": time.time(),
                   "serie": []}
    candado = threading.Lock()

    def en_vivo(linea):
        try:
            ev = json.loads(linea)
        except ValueError:
            return
        if ev.get("type") != "Point":
            return
        metrica, valor = ev.get("metric"), ev["data"]["value"]
        with candado:
            if metrica == "vus":
                estado_vivo["vus"] = int(valor)
            elif metrica == "http_req_duration":
                estado_vivo["duraciones"].append(valor)
            elif metrica == "http_reqs":
                estado_vivo["peticiones"] += 1
                estado_vivo["ventana"] += 1
            elif metrica == "http_req_failed" and valor == 1:
                estado_vivo["fallidas"] += 1
            ahora = time.time()
            if ahora - estado_vivo["ultimo"] >= 1.0:
                rps = estado_vivo["ventana"] / (ahora - estado_vivo["ultimo"])
                p95 = percentil(estado_vivo["duraciones"], 95)
                errores = 100 * estado_vivo["fallidas"] / max(1, estado_vivo["peticiones"])
                bus.emitir("carga", "metrica", vus=estado_vivo["vus"], peticiones=estado_vivo["peticiones"],
                           rps=round(rps, 1), p95=round(p95), errores_pct=round(errores, 2),
                           umbral=CONFIG["umbral_p95_ms"])
                estado_vivo["ventana"], estado_vivo["ultimo"] = 0, ahora

    if not herramienta("k6"):
        bus.emitir("carga", "paso", "k6 no está instalado", "FALLA")
        return {"estado": "ERROR", "resumen": "k6 no disponible", "metricas": {}, "informes": []}
    codigo, (resumen, base) = _k6("carga", perfil, carpeta, en_vivo)
    met = _metricas_k6(resumen)
    cumple = met["p95_ms"] < CONFIG["umbral_p95_ms"] and met["errores_pct"] < 1
    bus.emitir("carga", "paso",
               f"P95 {met['p95_ms']} ms (umbral {CONFIG['umbral_p95_ms']} ms) · errores {met['errores_pct']} % · "
               f"{met['peticiones']} peticiones", "PASA" if cumple else "FALLA")
    return {
        "estado": "PASA" if codigo == 0 and cumple else "FALLA",
        "resumen": f"perfil {perfil}: {met['peticiones']} peticiones, P95 {met['p95_ms']} ms, errores {met['errores_pct']} %",
        "metricas": dict(met, perfil=perfil),
        "informes": [f"{base}.html"] if base else [],
    }


def front(carpeta, ventana=True, caso="PagoTiendaTest#pagoAprobado"):
    """Prueba de interfaz con Serenity: los pasos (narrar) llegan en vivo desde el archivo demo.log."""
    mvn = herramienta("mvn")
    if not mvn:
        bus.emitir("front", "paso", "Maven no está instalado (ver README, sección Requisitos)", "FALLA")
        return {"estado": "ERROR", "resumen": "Maven no disponible", "metricas": {}, "informes": []}
    proyecto = RAIZ / "serenity-tienda"
    clase = caso.split("#")[0]
    for viejo in (proyecto / "target" / "surefire-reports").glob(f"TEST-*{clase}*.xml"):
        viejo.unlink()
    pasos_log = carpeta / "front-pasos.log"
    bus.emitir("front", "paso", f"Caso {caso} · Chrome {'visible' if ventana else 'sin ventana'}", "INFO")

    vistos = {"descarga": False, "chrome": False}

    def linea_mvn(linea):
        if not vistos["descarga"] and "Downloading from" in linea:
            vistos["descarga"] = True
            bus.emitir("front", "log", "Maven descarga dependencias (solo la primera vez; puede tardar unos minutos)…")
        if not vistos["chrome"] and ("ChromeDriver" in linea or "Running paylink" in linea):
            vistos["chrome"] = True
            bus.emitir("front", "log", "Abriendo Chrome…")

    cmd = [mvn, "-B", "test", "serenity:aggregate", f"-Dtest={caso}", f"-Ddemo.log={pasos_log}", f"-Dpaylink.url={BASE}",
           "-Dsurefire.failIfNoSpecifiedTests=false", "-Dmaven.test.failure.ignore=true"]
    if ventana:
        cmd += ["-Dventana=false", "-Ddemo=true"]
    with Cola(pasos_log, lambda l: bus.emitir("front", "paso", l.strip(), "INFO")):
        codigo = correr(cmd, cwd=proyecto, al_leer_linea=linea_mvn, log=carpeta / "front-maven.log")

    pruebas = fallos = 0
    detalle = ""
    for xml in (proyecto / "target" / "surefire-reports").glob(f"TEST-*{clase}*.xml"):
        raiz = ET.parse(xml).getroot()
        pruebas += int(raiz.get("tests", 0))
        fallos += int(raiz.get("failures", 0)) + int(raiz.get("errors", 0))
        error = raiz.find(".//failure") if raiz.find(".//failure") is not None else raiz.find(".//error")
        if error is not None:
            detalle = (error.get("message") or "").splitlines()[0][:200]
    informe = proyecto / "target" / "site" / "serenity" / "index.html"
    informes = []
    if informe.exists():
        informes.append("serenity-tienda/target/site/serenity/index.html")
        bus.emitir("front", "informe", "Informe Serenity (pasos con capturas)", ruta=informes[0])
    if pruebas == 0:
        bus.emitir("front", "paso", f"La prueba no llegó a ejecutarse (Maven terminó con código {codigo}); "
                                    f"ver {carpeta.name}/front-maven.log", "FALLA")
        return {"estado": "ERROR", "resumen": "Serenity no se ejecutó", "metricas": {}, "informes": informes}
    estado = "PASA" if fallos == 0 else "FALLA"
    bus.emitir("front", "paso", "Escenario completado sin errores" if estado == "PASA" else f"Falla: {detalle}", estado)
    return {"estado": estado, "resumen": f"{pruebas - fallos} de {pruebas} escenario(s) pasan" + (f": {detalle}" if detalle else ""),
            "metricas": {"escenarios": pruebas, "fallan": fallos}, "informes": informes}


def seguridad(carpeta):
    """Revisión pasiva (OWASP API Top 10). Solo corre si config.json registra la autorización del dueño."""
    autorizacion = CONFIG.get("autorizacion_seguridad") or {}
    if not autorizacion.get("otorgada"):
        bus.emitir("seguridad", "paso", "Sin autorización registrada en apis/paylink/config.json: no se ejecuta", "NO MEDIDO")
        return {"estado": "OMITIDO", "resumen": "sin autorización del dueño de la demo", "metricas": {}, "informes": []}
    if BASE != URL_DEMO:
        # La autorización cubre solo la URL de config.json. Otra URL necesita la autorización escrita de su dueño.
        bus.emitir("seguridad", "paso", f"La autorización cubre solo {URL_DEMO}; no se revisa {BASE}", "NO MEDIDO")
        return {"estado": "OMITIDO", "resumen": "URL sin autorización de seguridad", "metricas": {}, "informes": []}
    bus.emitir("seguridad", "paso", f"Revisión pasiva autorizada por {autorizacion.get('por')} ({autorizacion.get('fecha')})", "INFO")
    conteo = {"PASA": 0, "HALLAZGO": 0, "REVISAR": 0, "NO MEDIDO": 0}
    informe = {}

    def linea(texto):
        m = re.match(r"^(SEG-\S+) (PASA|HALLAZGO|REVISAR|NO MEDIDO) - (.*?) - (.*)$", texto)
        if m:
            id_, res, titulo, evidencia = m.groups()
            conteo[res] += 1
            bus.emitir("seguridad", "paso", f"{id_} {titulo} — {evidencia}", res)
        elif texto.startswith("Informe: "):
            informe["ruta"] = texto[len("Informe: "):].strip()

    codigo = correr([sys.executable, "demo/seguridad/revision.py"], cwd=RAIZ, env={"PAYLINK_URL": BASE}, al_leer_linea=linea,
                    log=carpeta / "seguridad.log", timeout=600)
    informes = []
    if "ruta" in informe:
        relativa = os.path.relpath(informe["ruta"], RAIZ).replace("\\", "/")
        informes.append(relativa)
        bus.emitir("seguridad", "informe", "Informe de seguridad", ruta=relativa)
    if codigo != 0:
        return {"estado": "ERROR", "resumen": f"la revisión terminó con código {codigo}", "metricas": conteo, "informes": informes}
    estado = "HALLAZGOS" if conteo["HALLAZGO"] or conteo["REVISAR"] else "PASA"
    return {"estado": estado,
            "resumen": f"{conteo['PASA']} de {conteo['PASA'] + conteo['HALLAZGO']} revisiones pasan, {conteo['HALLAZGO']} hallazgo(s)",
            "metricas": conteo, "informes": informes}


def fecha_hora():
    return datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
