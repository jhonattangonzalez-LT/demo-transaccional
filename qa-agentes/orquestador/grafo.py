"""Grafo de coordinación (LangGraph) de las pruebas de QA de PayLink.

    START -> calentar -+-> api -> front -> carga -+-> consolidar -> END
                       +-> seguridad -------------+

- api, front y carga reinician los datos de la demo, por eso van en secuencia (no se pisan).
- seguridad es pasiva (no cambia datos) y corre en paralelo.
- Cada nodo publica sus pasos en vivo en el bus de eventos (consola + panel web).
"""
import datetime
import json
import threading
from typing import Annotated, TypedDict

from langgraph.graph import END, START, StateGraph

import ejecutores
from eventos import bus

TIPOS = ["api", "front", "carga", "seguridad"]
NOMBRES = {"calentar": "Preparar", "api": "API: pago completo", "front": "Tienda en el navegador",
           "carga": "Rendimiento", "seguridad": "Seguridad básica", "consolidar": "Informe final"}


def _unir(a, b):
    return {**a, **b}


class Estado(TypedDict):
    tipos: list
    opciones: dict
    carpeta: str
    resultados: Annotated[dict, _unir]


def _nodo(nombre, funcion):
    """Envuelve un ejecutor: decide si corre, publica su estado y nunca rompe el grafo por una excepción."""

    def ejecutar(estado: Estado):
        import pathlib
        carpeta = pathlib.Path(estado["carpeta"])
        if nombre in TIPOS and nombre not in estado["tipos"]:
            bus.emitir(nombre, "nodo", "no solicitado", "omitido")
            return {"resultados": {nombre: {"estado": "OMITIDO", "resumen": "no solicitado", "metricas": {}, "informes": []}}}
        previo = estado["resultados"].get("calentar", {})
        if nombre != "calentar" and previo.get("estado") == "FALLA":
            bus.emitir(nombre, "nodo", "la demo no está disponible", "omitido")
            return {"resultados": {nombre: {"estado": "OMITIDO", "resumen": "demo no disponible", "metricas": {}, "informes": []}}}
        bus.emitir(nombre, "nodo", NOMBRES[nombre], "corriendo")
        try:
            resultado = funcion(carpeta, estado["opciones"])
        except Exception as e:  # el error queda registrado y el grafo sigue con los demás nodos
            bus.emitir(nombre, "paso", f"Error inesperado: {e}", "FALLA")
            resultado = {"estado": "ERROR", "resumen": str(e), "metricas": {}, "informes": []}
        visual = {"PASA": "ok", "HALLAZGOS": "hallazgos", "OMITIDO": "omitido"}.get(resultado["estado"], "falla")
        bus.emitir(nombre, "nodo", resultado["resumen"], visual)
        return {"resultados": {nombre: resultado}}

    return ejecutar


def _consolidar(carpeta, opciones, resultados):
    """Escribe el informe consolidado (Markdown + JSON) con los resultados reales de cada nodo."""
    ahora = datetime.datetime.now()
    filas = []
    for t in ["calentar"] + TIPOS:
        r = resultados.get(t, {})
        enlaces = ", ".join(f"`{i}`" for i in r.get("informes", [])) or "—"
        filas.append(f"| {NOMBRES[t]} | {r.get('estado', '—')} | {r.get('resumen', '')} | {enlaces} |")
    global_ok = all(r.get("estado") in ("PASA", "HALLAZGOS", "OMITIDO") for r in resultados.values())
    md = "\n".join([
        f"# Informe consolidado de QA · PayLink · {ahora:%Y-%m-%d %H:%M}",
        "",
        f"- URL probada: {ejecutores.BASE}",
        f"- Tipos solicitados: {', '.join(opciones['tipos'])} · perfil de carga: {opciones['perfil_carga']}",
        f"- Resultado general: **{'SIN FALLAS' if global_ok else 'CON FALLAS'}**",
        "",
        "| Prueba | Resultado | Resumen | Informes |",
        "|---|---|---|---|",
        *filas,
        "",
        f"Eventos paso a paso: `informes/ejecuciones/{carpeta.name}/eventos.jsonl`",
        "",
        "Las cifras salen de la ejecución. **Decisión humana: la persona del equipo.**",
        "",
    ])
    (carpeta / "consolidado.md").write_text(md, encoding="utf-8")
    (carpeta / "consolidado.json").write_text(
        json.dumps({"fecha": ahora.isoformat(timespec="seconds"), "demo": ejecutores.BASE, "opciones": opciones,
                    "resultado_general": "SIN FALLAS" if global_ok else "CON FALLAS", "resultados": resultados},
                   ensure_ascii=False, indent=2), encoding="utf-8")
    return global_ok


def construir():
    g = StateGraph(Estado)
    g.add_node("calentar", _nodo("calentar", lambda c, o: ejecutores.calentar(c)))
    g.add_node("api", _nodo("api", lambda c, o: ejecutores.api_funcional(c)))
    g.add_node("front", _nodo("front", lambda c, o: ejecutores.front(c, ventana=o["ventana"])))
    g.add_node("carga", _nodo("carga", lambda c, o: ejecutores.carga(c, perfil=o["perfil_carga"])))
    g.add_node("seguridad", _nodo("seguridad", lambda c, o: ejecutores.seguridad(c)))

    def consolidar(estado: Estado):
        import pathlib
        carpeta = pathlib.Path(estado["carpeta"])
        bus.emitir("consolidar", "nodo", NOMBRES["consolidar"], "corriendo")
        ok = _consolidar(carpeta, estado["opciones"], estado["resultados"])
        relativa = f"informes/ejecuciones/{carpeta.name}/consolidado.md"
        bus.emitir("consolidar", "informe", "Informe consolidado", ruta=relativa)
        bus.emitir("consolidar", "nodo", "SIN FALLAS" if ok else "CON FALLAS", "ok" if ok else "falla")
        return {"resultados": {"consolidar": {"estado": "PASA" if ok else "FALLA", "resumen": relativa,
                                              "metricas": {}, "informes": [relativa]}}}

    g.add_node("consolidar", consolidar)
    g.add_edge(START, "calentar")
    g.add_edge("calentar", "api")
    g.add_edge("calentar", "seguridad")
    g.add_edge("api", "front")
    g.add_edge("front", "carga")
    g.add_edge(["seguridad", "carga"], "consolidar")
    g.add_edge("consolidar", END)
    return g.compile()


_corriendo = threading.Lock()


def en_curso():
    return _corriendo.locked()


def ejecutar(tipos, perfil_carga="corta", ventana=True, url=None):
    """Corre el grafo completo y devuelve (ok_general, resultados, carpeta). Una sola ejecución a la vez."""
    if not _corriendo.acquire(blocking=False):
        raise RuntimeError("ya hay una ejecución en curso")
    try:
        return _ejecutar(tipos, perfil_carga, ventana, url)
    finally:
        _corriendo.release()


def _ejecutar(tipos, perfil_carga, ventana, url):
    ejecutores.usar_url(url)
    carpeta = ejecutores.RAIZ / "informes" / "ejecuciones" / ejecutores.fecha_hora()
    bus.nueva_ejecucion(carpeta)
    opciones = {"tipos": list(tipos), "perfil_carga": perfil_carga, "ventana": ventana, "url": ejecutores.BASE}
    bus.emitir("ejecucion", "ejecucion", f"Inicio · {ejecutores.BASE} · tipos: {', '.join(tipos)} · carga: {perfil_carga}",
               "inicio", id=carpeta.name, opciones=opciones)
    final = construir().invoke({"tipos": list(tipos), "opciones": opciones, "carpeta": str(carpeta), "resultados": {}})
    resultados = final["resultados"]
    ok = resultados.get("consolidar", {}).get("estado") == "PASA"
    bus.emitir("ejecucion", "ejecucion", "SIN FALLAS" if ok else "CON FALLAS", "fin", id=carpeta.name,
               resultados={k: {"estado": v["estado"], "resumen": v["resumen"], "informes": v.get("informes", [])}
                           for k, v in resultados.items()})
    return ok, resultados, carpeta
