# Demo de QA con agentes · PayLink

Demo de pruebas de la pasarela PayLink (datos ficticios).

## Estructura

```
apis/paylink/config.json     datos de la API (URL, clave demo, pagador, montos, umbral)
docs/                        swagger de PayLink y DATOS_DE_PRUEBA.md (fuentes)
k6/paylink.js                pruebas de carga y funcionales con k6 (genera el informe visual)
informes/carga/              informes HTML y JSON de cada corrida
informes/ejecuciones/        consolidado y eventos de cada corrida del coordinador (no se versiona)
orquestador/                 coordinador LangGraph (qa.py, grafo.py, ejecutores.py) y panel en vivo (panel.py/.html)
qa.cmd / qa.sh               lanzadores del coordinador (crean .venv con LangGraph la primera vez)
.claude/agents/              agentes: coordinador-qa, api-funcional, carga-api, front-serenity, seguridad-api
.claude/commands/qa.md       comando /qa → delega en coordinador-qa
```

## Coordinador
- `qa.cmd` (Windows) o `./qa.sh`: corre api, front, carga y seguridad con el panel en http://127.0.0.1:8792.
- Opciones: `--tipos api,front,carga,seguridad`, `--carga corta|carga|estres`, `--url <url con la API de PayLink>`, `--sin-ventana`, `--sin-panel`, `--salir-al-terminar`.
- Pruebas: API (6 validaciones), tienda (1 compra en Chrome), rendimiento (k6) y seguridad (5 revisiones pasivas).
- Seguridad solo corre con `autorizacion_seguridad.otorgada: true` en `apis/paylink/config.json`, registrada por el dueño de la demo, y solo contra la `base_url` de ese archivo.

## Cómo correr las pruebas

```
cd <carpeta-del-repo>
TIPO=smoke  ~/.local/bin/k6 run k6/paylink.js   # 1 pago aprobado y 1 rechazado, con todas las validaciones (~5 s)
TIPO=carga  ~/.local/bin/k6 run k6/paylink.js   # 10 usuarios durante 2 min (~3 min)
TIPO=estres ~/.local/bin/k6 run k6/paylink.js   # sube hasta 50 usuarios (~3,5 min)
```

Cada corrida deja `informes/carga/<fecha>-paylink-<tipo>.html`: se abre en el navegador y muestra
tiempos (promedio, P95), errores HTTP y cada prueba funcional con su resultado (PASA / FALLA).
La corrida reinicia los datos de la demo al empezar (`/api/v1/demo/reset`).

## Reglas
- Solo se prueba la API indicada en `config.json`. Nunca producción.
- Datos de prueba ficticios (los de `docs/DATOS_DE_PRUEBA.md`). Nunca datos reales.
- Las cifras salen de la ejecución. Las decisiones las toma una persona.

## Pruebas de interfaz (Serenity) · tienda PayLink

Proyecto Maven en `serenity-tienda/`. Prueba la tienda web en Chrome headless: tienda → checkout → banco → resultado.
Escenarios de flujo (`PagoTiendaTest`): pago aprobado (Ana, $150.000), pago rechazado (Ana, $80.000), fondos insuficientes (Luis, $50.000).
Escenarios de ingreso de datos (`DatosFormularioTest`): valor vacío no avanza; documento que no existe en el banco elegido muestra error; clave incorrecta muestra «Credenciales bancarias inválidas» y no avanza.

```
cd <carpeta-del-repo>/serenity-tienda
mvn verify
```

Informe visual: `serenity-tienda/target/site/serenity/index.html` (pasos, capturas y resultado de cada escenario).
Un navegador nuevo por escenario: en la misma sesión, el segundo envío de la tienda no avanzaba.

## Pendiente
- Cuando se agregue otra API: copiar `apis/paylink/` a `apis/<nombre>/` y ajustar su `config.json` desde su swagger.
