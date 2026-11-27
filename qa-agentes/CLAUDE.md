# Demo de QA con agentes · PayLink

Demo de pruebas de la pasarela PayLink (datos ficticios).

## Estructura

```
apis/paylink/config.json     datos de la API (URL, clave demo, pagador, montos, umbral)
docs/                        swagger de PayLink y DATOS_DE_PRUEBA.md (fuentes)
k6/paylink.js                pruebas de carga y funcionales con k6 (genera el informe visual)
informes/carga/              informes HTML y JSON de cada corrida
.claude/agents/              agentes (carga-api, api-funcional)
```

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
