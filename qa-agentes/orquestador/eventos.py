"""Bus de eventos de una ejecución: lo usan los nodos del grafo, la consola y el panel web.

Cada evento es un dict con: seq, ts, tipo (nodo del grafo), clase, texto, estado y datos.
Clases:
  ejecucion  inicio / fin de toda la corrida
  nodo       cambio de estado de un nodo del grafo (corriendo, ok, falla, omitido)
  paso       un paso de una prueba (PASA, FALLA, HALLAZGO, REVISAR, NO MEDIDO, INFO)
  metrica    métricas en vivo de la prueba de carga
  informe    enlace a un informe generado
  log        texto informativo
Los eventos también se guardan en informes/ejecuciones/<id>/eventos.jsonl.
"""
import datetime
import json
import pathlib
import threading

ICONOS = {"PASA": "OK ", "FALLA": "X  ", "HALLAZGO": "!  ", "REVISAR": "?  ", "NO MEDIDO": "-  "}


class Bus:
    def __init__(self):
        self._eventos = []
        self._cond = threading.Condition()
        self._archivo = None
        self.inicio_actual = 0
        self.consola = True

    def nueva_ejecucion(self, carpeta: pathlib.Path):
        with self._cond:
            if self._archivo:
                self._archivo.close()
            carpeta.mkdir(parents=True, exist_ok=True)
            self._archivo = open(carpeta / "eventos.jsonl", "a", encoding="utf-8")
            self.inicio_actual = len(self._eventos)

    def emitir(self, tipo, clase, texto="", estado=None, **datos):
        with self._cond:
            ev = {
                "seq": len(self._eventos),
                "ts": datetime.datetime.now().isoformat(timespec="seconds"),
                "tipo": tipo,
                "clase": clase,
                "texto": texto,
                "estado": estado,
                "datos": datos,
            }
            self._eventos.append(ev)
            if self._archivo:
                self._archivo.write(json.dumps(ev, ensure_ascii=False) + "\n")
                self._archivo.flush()
            self._cond.notify_all()
        if self.consola:
            self._imprimir(ev)
        return ev

    def total(self):
        with self._cond:
            return len(self._eventos)

    def desde(self, seq, timeout=15.0):
        """Devuelve los eventos con número >= seq; si no hay, espera hasta timeout segundos."""
        with self._cond:
            if seq >= len(self._eventos):
                self._cond.wait(timeout)
            return self._eventos[seq:]

    @staticmethod
    def _imprimir(ev):
        hora = ev["ts"][11:19]
        clase, tipo, texto, estado = ev["clase"], ev["tipo"], ev["texto"], ev["estado"]
        if clase == "metrica":
            d = ev["datos"]
            texto = (f"usuarios {d.get('vus', 0):>3} | peticiones {d.get('peticiones', 0):>5} | "
                     f"{d.get('rps', 0):>5.1f} req/s | P95 {d.get('p95', 0):>6.0f} ms | errores {d.get('errores_pct', 0):.2f} %")
            linea = f"[{hora}] {tipo:<10}      {texto}"
        elif clase == "paso":
            linea = f"[{hora}] {tipo:<10} {ICONOS.get(estado, '   ')}  {texto}"
        elif clase == "nodo":
            linea = f"[{hora}] {tipo:<10} == {estado.upper()} {('- ' + texto) if texto else ''}"
        elif clase == "informe":
            linea = f"[{hora}] {tipo:<10} informe: {ev['datos'].get('ruta')}"
        else:
            linea = f"[{hora}] {tipo:<10} {texto}"
        print(linea, flush=True)


bus = Bus()
