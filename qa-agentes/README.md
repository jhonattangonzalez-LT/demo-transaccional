# Demo de QA · PayLink (API y tienda)

Demo de pruebas de la pasarela de pagos PayLink (demo de el dueño de la demo, en Vercel):
un caso por tipo de prueba, con Chrome visible en los casos de formulario.

Todos los datos son ficticios.

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
