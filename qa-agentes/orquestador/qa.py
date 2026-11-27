"""Coordinador de QA de PayLink (LangGraph). Ejecutable de consola.

Uso:
  python orquestador/qa.py                          # las 4 pruebas, con panel en vivo y Chrome visible
  python orquestador/qa.py --tipos api,seguridad    # solo algunas
  python orquestador/qa.py --carga carga            # perfil de carga: corta (defecto) | carga | estres
  python orquestador/qa.py --url https://otra-url   # otra URL con la misma API de PayLink
  python orquestador/qa.py --sin-panel --sin-ventana  # solo consola, Chrome sin ventana (CI / agente)
  python orquestador/qa.py panel                    # solo el panel, con botones para lanzar
  python orquestador/qa.py grafo                    # imprime el grafo (Mermaid)

Código de salida: 0 sin fallas, 1 con fallas (ejecución directa).
"""
import argparse
import pathlib
import sys
import time
import webbrowser

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

import grafo  # noqa: E402
import panel  # noqa: E402


def url_valida(texto):
    texto = texto.strip().rstrip("/")
    if not texto.startswith(("http://", "https://")) or " " in texto:
        raise argparse.ArgumentTypeError("la URL debe empezar por http:// o https://")
    return texto


def tipos_validos(texto):
    tipos = [t.strip() for t in texto.split(",") if t.strip()]
    invalidos = [t for t in tipos if t not in grafo.TIPOS]
    if invalidos or not tipos:
        raise argparse.ArgumentTypeError(f"tipos válidos: {', '.join(grafo.TIPOS)}")
    return tipos


def main():
    p = argparse.ArgumentParser(description="Coordinador de pruebas de QA de PayLink (LangGraph)")
    p.add_argument("modo", nargs="?", choices=["ejecutar", "panel", "grafo"], default="ejecutar")
    p.add_argument("--tipos", type=tipos_validos, default=list(grafo.TIPOS),
                   help="api,front,carga,seguridad (por defecto, todas)")
    p.add_argument("--carga", choices=["corta", "carga", "estres"], default="corta", help="perfil de carga (k6)")
    p.add_argument("--sin-ventana", action="store_true", help="Chrome sin ventana (headless) en la prueba de front")
    p.add_argument("--sin-panel", action="store_true", help="no abrir el panel web; solo consola")
    p.add_argument("--salir-al-terminar", action="store_true", help="cerrar el panel cuando termine la ejecución")
    p.add_argument("--no-abrir", action="store_true", help="no abrir el navegador automáticamente")
    p.add_argument("--url", type=url_valida, default=None,
                   help="URL a probar (por defecto, la de apis/paylink/config.json); debe tener la API de PayLink")
    p.add_argument("--puerto", type=int, default=8792)
    a = p.parse_args()

    if a.modo == "grafo":
        print(grafo.construir().get_graph().draw_mermaid())
        return 0

    url = f"http://127.0.0.1:{a.puerto}"
    if a.modo == "panel" or not a.sin_panel:
        try:
            panel.servir(a.puerto)
        except OSError:
            # Quedó abierta una ventana de una ejecución anterior: se cierra y se toma su lugar.
            resultado = panel.reemplazar_panel_anterior(a.puerto)
            if isinstance(resultado, str):
                print(resultado, flush=True)
                return 2
            print("Se cerró el panel de la ejecución anterior.", flush=True)
        print(f"Panel en vivo: {url}", flush=True)
        if not a.no_abrir:
            webbrowser.open(url)

    if a.modo == "panel":
        print("Lanza las pruebas desde el panel. Ctrl+C para salir.", flush=True)
        return esperar_ctrl_c()

    if not a.sin_panel:
        time.sleep(1.5)  # deja que el panel se conecte antes del primer evento
    ok, resultados, carpeta = grafo.ejecutar(a.tipos, a.carga, ventana=not a.sin_ventana, url=a.url)

    print("\nResultado:", "SIN FALLAS" if ok else "CON FALLAS")
    for t in ["calentar"] + grafo.TIPOS:
        r = resultados.get(t)
        if r:
            print(f"  {grafo.NOMBRES[t]:<24} {r['estado']:<10} {r['resumen']}")
    print(f"Informe consolidado: {carpeta / 'consolidado.md'}")

    if not a.sin_panel and not a.salir_al_terminar:
        print(f"\nEl panel sigue abierto en {url} para revisar los informes. Ctrl+C para salir.", flush=True)
        esperar_ctrl_c()
    return 0 if ok else 1


def esperar_ctrl_c():
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    sys.exit(main())
