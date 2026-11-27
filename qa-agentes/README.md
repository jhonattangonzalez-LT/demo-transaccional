# Demo de QA · PayLink (API y tienda)

Demo de pruebas de la pasarela de pagos PayLink (demo de el dueño de la demo, en Vercel):
un caso por tipo de prueba, con Chrome visible en los casos de formulario.

Todos los datos son ficticios.

## Cómo ejecutarlo (lo más fácil)

1. **Doble clic en `qa.cmd`** (en Windows). En Linux o macOS: `./qa.sh`.
2. Se abre el panel de Quind en el navegador (`http://127.0.0.1:8792`) y las 4 pruebas corren solas, paso a paso.
3. Al final, el panel muestra el resultado de cada prueba y los enlaces a los informes.

Para volver a correr, usa el botón **Ejecutar** del panel. Ahí también puedes elegir qué pruebas correr y la URL.

| # | Prueba | Qué valida | Cuándo pasa |
|---|---|---|---|
| 1 | API: pago completo | 6 validaciones: crear la orden, iniciar el pago, iniciar sesión en el banco, aprobar, saldos y rechazo | Las 6 pasan |
| 2 | Tienda en el navegador | Una compra en Chrome: tienda → pago → banco → resultado | La compra termina aprobada |
| 3 | Rendimiento | 5 usuarios pagando a la vez durante 45 s | El 95 % responde en menos de 1 s y casi sin errores |
| 4 | Seguridad básica | 5 revisiones sin ataques: claves, errores, rutas de administración, encabezados, clave en la página | Cada revisión sin hallazgos |

### ¿Sirve para cualquier URL?

Sirve para **cualquier URL que tenga la API de PayLink** (los mismos endpoints de `docs/openapi_demo_transaccional.json`
y los datos de prueba de `docs/DATOS_DE_PRUEBA.md`): por ejemplo, la demo local (`http://localhost:8080`), otro
despliegue o un ambiente de QA. No sirve para una página web cualquiera: si la URL no tiene la API, el paso
«Preparar» lo dice y no corre nada más.

- En el panel: escribe la URL en el campo **URL** y pulsa **Ejecutar**.
- En consola: `qa.cmd --url http://localhost:8080`.
- **Seguridad** solo corre contra la URL autorizada en `apis/paylink/config.json`. Con otra URL se omite: hace falta
  la autorización escrita de su dueño.

## Requisitos

| Herramienta | Para qué | Cómo comprobarla |
|---|---|---|
| JDK 21 **completo** (con `javac`) | Serenity (pruebas de la tienda) | `javac -version` |
| Maven 3.9+ | Serenity | `mvn -version` |
| k6 | Carga y API | `k6 version` |
| Python 3.10+ | Revisión de seguridad y servidor de la presentación | `python3 --version` |
| Google Chrome | Ver las pruebas de formulario en pantalla | `google-chrome --version` |
| Internet | Maven baja dependencias; la demo corre en Vercel | — |

Si alguna herramienta no está en el `PATH`, el script la busca en `~/.local/bin`. Si no aparece, lo indica.

## Descargar el proyecto

```bash
git clone https://github.com/KevinLopezB/paylink-qa-agentes.git
cd paylink-qa-agentes
chmod +x demo/ejecutar_demo.sh
```

## Instalar las herramientas

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven python3 git
# k6: https://k6.io/docs/get-started/installation/ (paquete oficial para Linux)
# Google Chrome: https://www.google.com/chrome/ (paquete .deb oficial)
```

**macOS:** con Homebrew:
```bash
brew install openjdk@21 maven k6 python git
brew install --cask google-chrome
```

**Windows:** instala Git para Windows, que trae Git Bash, y ejecuta los comandos desde Git Bash o WSL.
Instala JDK 21 (Eclipse Temurin), Maven, k6, Python 3 y Google Chrome con sus instaladores oficiales.

Verifica cada herramienta con los comandos de la sección de requisitos. Java debe ser la versión 21 y tener `javac`.

## Correr la demo

```bash
./demo/ejecutar_demo.sh
```

Corre, en orden:
1. **Funcional de flujo** (front): pago aprobado en Chrome, con un rótulo por paso.
2. **Funcional de ingreso de datos** (front): documento en banco equivocado, en Chrome.
3. **No funcional** (carga): 10 usuarios durante 3 minutos con k6 (sin navegador).
4. **Seguridad**: revisión pasiva de la API y la tienda (sin ataques ni carga).

Al final abre en Chrome los informes de carga y de Serenity. Los informes de seguridad quedan en `informes/seguridad/`.

## Coordinador con LangGraph y panel en vivo

El coordinador corre las pruebas como un grafo (LangGraph) y muestra cada paso en vivo en un panel web local
(`http://127.0.0.1:8792`) y en la consola. Funciona en Windows, Linux y macOS.

```
                 ┌─> api (k6 smoke) ─> front (Serenity, Chrome) ─> carga (k6) ─┐
START ─> calentar┤                                                             ├─> consolidar ─> END
                 └─> seguridad (pasiva, en paralelo) ──────────────────────────┘
```

- `calentar` despierta la demo de Vercel antes de medir (evita P95 falsos por arranque en frío).
- `api`, `front` y `carga` reinician los datos de la demo, por eso van en secuencia; `seguridad` no cambia datos y va en paralelo.
- `consolidar` escribe `informes/ejecuciones/<fecha-hora>/consolidado.md` (y `.json`, más `eventos.jsonl` con cada paso).

**Requisitos adicionales:** Python 3.10+ (el primer uso crea `.venv` e instala LangGraph solo).

```bash
qa.cmd                                 # Windows: las 4 pruebas, panel en vivo y Chrome visible
./qa.sh                                # Linux / macOS / Git Bash
qa.cmd --tipos api,carga               # solo algunas: api, front, carga, seguridad
qa.cmd --carga carga                   # perfil de carga: corta (5 usuarios ~45 s, defecto) | carga | estres
qa.cmd --url http://localhost:8080     # otra URL con la API de PayLink (seguridad se omite)
qa.cmd --sin-panel --sin-ventana       # solo consola y Chrome sin ventana (CI); código de salida 0/1
qa.cmd panel                           # solo el panel, con botones para lanzar
qa.cmd grafo                           # imprime el grafo en Mermaid
```

Qué se ve en el panel, una prueba por tipo:

| Tipo | En vivo |
|---|---|
| API funcional | Cada validación del flujo (crear → iniciar → autenticar → autorizar → saldos → rechazo) con PASA/FALLA |
| Front | Cada paso de Serenity (tienda → checkout → banco → resultado) mientras Chrome lo ejecuta en pantalla; enlace al informe con capturas |
| Carga | Usuarios activos, peticiones/s, P95 y errores cada segundo, con gráfica y línea de umbral |
| Seguridad | Cada revisión (SEG-01…SEG-05) con PASA / HALLAZGO |

**Seguridad requiere autorización registrada.** El nodo solo corre si `apis/paylink/config.json` tiene:

```json
"autorizacion_seguridad": { "otorgada": true, "por": "<dueño de la demo>", "fecha": "AAAA-MM-DD", "alcance": "solo revisión pasiva de <URL>" }
```

Sin eso, el panel la muestra como «omitida». Ese registro lo hace el dueño de la demo, no el coordinador.

### Desde Claude Code (agente coordinador)

- En una sesión de Claude Code dentro del repo: `/qa todo`, `/qa solo api y carga`, `/qa estrés`…
  o pídelo en lenguaje natural («usa el coordinador-qa para correr la prueba de front»).
- Desde consola, sin abrir la sesión interactiva:
  ```bash
  claude -p "/qa api y carga" --allowedTools "Bash(qa.cmd:*)" "Bash(./qa.sh:*)" "Read"
  ```

El agente `coordinador-qa` traduce el pedido a opciones de `qa.py`, lanza el grafo (con el panel en vivo) y resume
el informe consolidado. Los otros cuatro agentes siguen disponibles para trabajo puntual de cada tipo.

## Presentación con botones

```bash
python3 demo/servidor.py
```

Abre **http://127.0.0.1:8791** en Chrome. Cada caso tiene su botón y muestra el resultado en la misma diapositiva.
Solo escucha en tu equipo (127.0.0.1).

## Pruebas por separado

```bash
cd serenity-tienda && mvn -B verify          # suite completa + informe en target/site/serenity/index.html
TIPO=smoke k6 run k6/paylink.js              # validaciones de la API (más rápido)
TIPO=carga k6 run k6/paylink.js              # 10 usuarios, 3 min
TIPO=estres k6 run k6/paylink.js             # hasta 50 usuarios, unos 3,5 min
python3 demo/seguridad/revision.py           # revisión pasiva de seguridad
```

Por defecto, las pruebas de Serenity corren sin ventana. Para ver Chrome en pantalla, agrega `-Dventana=false -Ddemo=true`
al comando de `mvn test` (requiere `DISPLAY` configurado en Linux).

## Estructura

- `apis/paylink/config.json`: URL, clave de la demo, pagador y umbrales.
- `k6/paylink.js`: pruebas de carga y de API con informe HTML.
- `serenity-tienda/`: pruebas de la tienda y el checkout (Serenity + Selenium).
- `demo/`: lanzador, presentación, servidor de botones y revisión de seguridad.
- `docs/`: datos de prueba y especificación de la demo.
- `.claude/agents/`: definición de los cuatro agentes (carga-api, api-funcional, front-serenity, seguridad-api).

## Límites conocidos

- Un solo comercio en la demo: el acceso entre comercios no se puede medir.
- Con 50 usuarios, el P95 supera 1 segundo; el umbral de 1 segundo lo definimos nosotros y falta confirmarlo.
- El reinicio de datos de la demo responde 500 a veces; las pruebas reintentan hasta 3 veces.
- Pruebas contra la demo pública: no correr cargas largas sin avisar al dueño.

## Autorización

La revisión de seguridad se hizo con autorización de el dueño de la demo.
Para cualquier otra URL, pide autorización por escrito antes de correr nada.
