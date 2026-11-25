# Prompt: Mockup de pasarela de pagos simulada "PayLink" (estilo PSE)

## Rol
Actúa como **Desarrollador Backend Senior Java / Spring Boot** con experiencia en sistemas de pagos y transferencias bancarias (flujos tipo PSE/ACH), APIs REST y pruebas con Postman. Priorizas la **simplicidad**, el código limpio y que **todo funcione de punta a punta a la primera**. Evitas la sobreingeniería: nada de microservicios, colas, Docker, seguridad compleja ni integraciones externas.

## Objetivo
Construir un **monolito Spring Boot** que simule una pasarela de pagos por débito a cuenta bancaria, al estilo PSE, pero con **nombres ficticios** (no usar "PSE", "ACH Colombia" ni nombres de bancos reales). Debe permitir:

1. Que un comercio cree una orden de pago.
2. Que el pagador recorra el paso a paso de la pasarela (selección de banco, datos, autenticación simulada, aprobación).
3. Que se **debite el dinero de la cuenta del pagador y se acredite en la cuenta del comercio**.
4. Que se **verifique** el estado de la transacción y los saldos.
5. Que todo sea probable desde una **colección Postman completa** y también desde unas pantallas web mínimas.

## Nombres ficticios
- Pasarela: **PayLink**
- Bancos simulados: **Banco Andino**, **Banco Cóndor**, **Banco Pacífico Digital**
- Comercio de ejemplo: **Tienda Demo S.A.S.**

## Stack (supuestos; ajustar si el equipo indica otras versiones)
- Java 21, Spring Boot 3.x, Maven
- Spring Web, Spring Data JPA, Validation, Thymeleaf
- Base de datos **H2 en memoria** con datos semilla (`data.sql`)
- Lombok opcional
- Puerto `8080`, consola H2 habilitada en `/h2-console`

## Estructura del proyecto (un solo módulo)
```
com.quind.paylink
 ├─ controller   (API REST + controladores web Thymeleaf)
 ├─ service      (PaymentService, BankService, AccountService)
 ├─ repository
 ├─ model        (entidades JPA + enums)
 ├─ dto          (request/response)
 └─ exception    (GlobalExceptionHandler con respuestas JSON uniformes)
```

## Modelo de datos (mínimo)
- **Bank**: id, code (`ANDINO`, `CONDOR`, `PACIFICO`), name.
- **Account**: id, number, holderName, documentType (`CC`, `NIT`, `CE`), documentNumber, bankId, balance (`BigDecimal`), type (`PAYER` | `MERCHANT`).
- **Merchant**: id, name, nit, accountId, apiKey (simple, en header `X-Api-Key`).
- **Transaction**: id (UUID), reference (única, generada), merchantId, amount, description, payerBankCode, payerDocumentType, payerDocumentNumber, personType (`NATURAL` | `JURIDICA`), status, createdAt, updatedAt, returnUrl.
- **TransactionStatus**: `CREATED` → `PENDING` → `APPROVED` | `REJECTED` | `FAILED` | `EXPIRED`.

Datos semilla: 3 bancos, 1 comercio con su cuenta, y al menos 3 cuentas de pagadores (una con saldo suficiente, una con saldo bajo para probar "fondos insuficientes", una en otro banco). Incluir en el README las credenciales simuladas de cada pagador.

## Flujo paso a paso (el "camino feliz")
1. **Comercio crea la orden** → `POST /api/v1/payments` → estado `CREATED`, devuelve `transactionId`, `reference` y `checkoutUrl`.
2. **Pagador consulta bancos** → `GET /api/v1/banks`.
3. **Pagador inicia el pago** (elige banco, tipo de persona, tipo y número de documento) → `POST /api/v1/payments/{id}/start` → estado `PENDING`, devuelve `bankRedirectUrl`.
4. **Banco simulado autentica** al pagador (documento + clave simulada, p. ej. `1234`) → `POST /api/v1/bank/{bankCode}/authenticate` → devuelve `bankSessionToken` simple (UUID en memoria).
5. **Pagador autoriza o rechaza** el débito → `POST /api/v1/bank/{bankCode}/authorize` con `{ transactionId, bankSessionToken, approve: true|false }`.
   - Si aprueba y hay saldo: **en una sola transacción `@Transactional`** se debita la cuenta del pagador, se acredita la del comercio y el estado pasa a `APPROVED`.
   - Si no hay saldo: `REJECTED` con motivo `INSUFFICIENT_FUNDS` (sin mover dinero).
   - Si rechaza: `REJECTED` con motivo `USER_CANCELLED`.
6. **Verificación**:
   - `GET /api/v1/payments/{id}` y `GET /api/v1/payments/reference/{reference}` → estado y detalle.
   - `GET /api/v1/accounts/{number}/balance` → saldo actual para confirmar débito/crédito.
   - `GET /api/v1/accounts/{number}/movements` → movimientos (débito/crédito) asociados a la transacción.
7. **Retorno al comercio**: pantalla de resultado con estado, referencia, valor, banco y fecha.

## Reglas de negocio (simples)
- Monto > 0 y con máximo 2 decimales; usar siempre `BigDecimal`.
- No se puede iniciar ni autorizar una transacción que no esté en el estado esperado (responder `409 Conflict`).
- Una transacción aprobada no se puede volver a procesar (idempotencia básica por estado).
- El documento del pagador debe coincidir con una cuenta del banco elegido; si no, `404` con mensaje claro.
- Transacciones en `PENDING` por más de 15 minutos se consideran `EXPIRED` al consultarlas (sin schedulers).
- Errores en formato uniforme: `{ "timestamp", "status", "error", "message", "path" }`.

## Pantallas web mínimas (Thymeleaf, sin frameworks JS)
1. `/checkout/{transactionId}`: resumen del pago + selección de banco, tipo de persona y documento.
2. `/bank/{bankCode}/login?tx=...`: login simulado del banco con su nombre ficticio.
3. `/bank/{bankCode}/authorize?tx=...`: confirmar o rechazar el débito mostrando monto y comercio.
4. `/result/{transactionId}`: comprobante con el estado final.

Las pantallas deben reutilizar los mismos servicios que la API REST. Estilo sencillo con CSS plano, aspecto de pasarela bancaria (logo de texto "PayLink", barra de pasos 1-2-3-4).

## Colección Postman (entregable obligatorio)
Generar `postman/PayLink.postman_collection.json` (formato v2.1) y `postman/PayLink.postman_environment.json` con:
- Variables: `baseUrl`, `apiKey`, `transactionId`, `reference`, `bankCode`, `bankSessionToken`, `payerAccount`, `merchantAccount`.
- Carpetas:
  1. **Setup**: listar bancos, consultar saldos iniciales.
  2. **Pago aprobado (camino feliz)**: crear → iniciar → autenticar → autorizar (approve=true) → consultar estado → verificar saldos y movimientos.
  3. **Pago rechazado por el usuario**.
  4. **Fondos insuficientes**.
  5. **Errores de validación**: monto negativo, API key inválida, banco inexistente, documento inexistente, transición de estado inválida (409).
- Cada request con **scripts de test** (`pm.test`) que validen el status code y campos clave, y que **guarden variables automáticamente** (`pm.collectionVariables.set`) para encadenar el flujo.
- Debe poder ejecutarse completa con el **Collection Runner** en orden y pasar todos los tests sobre una base recién iniciada.

## Pruebas
- Pruebas unitarias de `PaymentService` (aprobado, rechazado, fondos insuficientes, transición inválida).
- Una prueba de integración con `@SpringBootTest` + `MockMvc` del camino feliz completo verificando saldos finales.

## Entregables
1. Código fuente completo y compilable (`mvn spring-boot:run` sin configuración adicional).
2. `data.sql` con datos semilla.
3. Colección y environment de Postman.
4. `README.md` con: cómo ejecutar, diagrama de secuencia simple (Mermaid) del flujo, tabla de endpoints, cuentas/credenciales de prueba y cómo correr la colección.

## Restricciones
- Mantenerlo **simple**: sin Spring Security, sin JWT, sin Docker, sin mensajería, sin integraciones reales.
- Es un **mockup educativo/demostrativo**: no usar datos personales reales; todos los nombres, documentos y cuentas deben ser ficticios.
- No usar marcas reales (PSE, ACH, nombres de bancos existentes) en código, textos ni pantallas.
- Antes de entregar, verifica que el proyecto compila, que los tests pasan y que la colección Postman corre completa sin fallos.
