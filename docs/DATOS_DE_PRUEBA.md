# PayLink — Datos de prueba

> Todos los datos son **ficticios** y se cargan desde `src/main/resources/data.sql` en cada arranque
> (H2 en memoria: al reiniciar la aplicación los saldos vuelven a sus valores iniciales).
> No usar datos personales reales en este ambiente.

- URL base: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Moneda: COP (montos con máximo 2 decimales)

## 1. Bancos

| Código (`bankCode`) | Nombre |
|---|---|
| `ANDINO` | Banco Andino |
| `CONDOR` | Banco Cóndor |
| `PACIFICO` | Banco Pacífico Digital |

## 2. Comercio

| Campo | Valor |
|---|---|
| Nombre | Tienda Demo S.A.S. |
| NIT | `900000001` |
| API key (header `X-Api-Key`) | `demo-api-key-123` |
| Cuenta recaudadora | `9000000001` (Banco Andino) |
| Saldo inicial | 1.000.000,00 |

## 3. Pagadores

Clave simulada del portal bancario para **todos** los pagadores: `1234`.

| Titular | Banco | Tipo doc. | Documento | Cuenta | Saldo inicial | Tipo persona sugerido | Escenario |
|---|---|---|---|---|---|---|---|
| Ana Prueba Ficticia | `ANDINO` | `CC` | `1000000001` | `1000000001` | 5.000.000,00 | `NATURAL` | Pago aprobado / rechazado por el usuario |
| Luis Saldo Bajo | `ANDINO` | `CC` | `1000000002` | `1000000002` | 10.000,00 | `NATURAL` | Fondos insuficientes (pagar > 10.000) |
| Marta Ejemplo Cóndor | `CONDOR` | `CE` | `2000000001` | `2000000001` | 2.000.000,00 | `NATURAL` | Pago desde otro banco |
| Empresa Ficticia Ltda. | `PACIFICO` | `NIT` | `800000001` | `3000000001` | 10.000.000,00 | `JURIDICA` | Persona jurídica |

> El documento debe coincidir con una cuenta **del banco elegido**: p. ej. `CC 1000000001` existe en `ANDINO`,
> pero en `CONDOR` responde `404`.

## 4. Valores permitidos (enums)

| Campo | Valores |
|---|---|
| `personType` | `NATURAL`, `JURIDICA` |
| `documentType` | `CC`, `NIT`, `CE` |
| `status` | `CREATED`, `PENDING`, `APPROVED`, `REJECTED`, `FAILED`, `EXPIRED` |
| `statusReason` | `INSUFFICIENT_FUNDS`, `USER_CANCELLED`, `PENDING_TIMEOUT` (o `null`) |
| Tipo de movimiento | `DEBIT`, `CREDIT` |

Transiciones: `CREATED → PENDING → APPROVED | REJECTED | EXPIRED`. Una transacción `PENDING` por más de
15 minutos pasa a `EXPIRED` al consultarla. El `bankSessionToken` es de un solo uso y vence en 10 minutos.

## 5. Escenarios

### 5.1 Pago aprobado (camino feliz) — Ana, Banco Andino

| Paso | Request | Body | Resultado esperado |
|---|---|---|---|
| 1 | `POST /api/v1/payments` + `X-Api-Key: demo-api-key-123` | `{"amount": 150000.00, "description": "Compra aprobada", "returnUrl": "https://tienda-demo.example/retorno"}` | `201`, `status=CREATED`; guardar `transactionId` |
| 2 | `POST /api/v1/payments/{transactionId}/start` | `{"bankCode": "ANDINO", "personType": "NATURAL", "documentType": "CC", "documentNumber": "1000000001"}` | `200`, `status=PENDING`, `bankRedirectUrl` |
| 3 | `POST /api/v1/bank/ANDINO/authenticate` | `{"transactionId": "{transactionId}", "documentType": "CC", "documentNumber": "1000000001", "password": "1234"}` | `200`; guardar `bankSessionToken` |
| 4 | `POST /api/v1/bank/ANDINO/authorize` | `{"transactionId": "{transactionId}", "bankSessionToken": "{token}", "approve": true}` | `200`, `status=APPROVED`, `statusReason=null` |
| 5 | `GET /api/v1/accounts/1000000001/balance` | — | `4850000.00` (5.000.000 − 150.000) |
| 6 | `GET /api/v1/accounts/9000000001/balance` | — | `1150000.00` (1.000.000 + 150.000) |
| 7 | `GET /api/v1/accounts/1000000001/movements?transactionId={transactionId}` | — | 1 movimiento `DEBIT` por 150000.00 |

> Los saldos esperados asumen la aplicación recién iniciada y este como primer pago.

### 5.2 Pago rechazado por el usuario — Ana, Banco Andino

Igual que 5.1 (por ejemplo con `amount: 80000.00`), pero en el paso 4 se envía `"approve": false`.

Resultado: `200`, `status=REJECTED`, `statusReason=USER_CANCELLED`. Los saldos no cambian y no hay movimientos
para esa transacción.

### 5.3 Fondos insuficientes — Luis, Banco Andino

| Paso | Diferencia frente a 5.1 |
|---|---|
| 1 | `amount: 50000.00` (mayor que el saldo de 10.000,00) |
| 2 y 3 | `documentNumber: "1000000002"` |
| 4 | `"approve": true` |

Resultado: `200`, `status=REJECTED`, `statusReason=INSUFFICIENT_FUNDS`. La cuenta `1000000002` sigue en
`10000.00` y el comercio no recibe crédito.

### 5.4 Otro banco — Marta, Banco Cóndor

Paso 2: `{"bankCode": "CONDOR", "personType": "NATURAL", "documentType": "CE", "documentNumber": "2000000001"}`.
Pasos 3 y 4 contra `/api/v1/bank/CONDOR/...` con `documentType: "CE"`, `documentNumber: "2000000001"`, clave `1234`.

### 5.5 Persona jurídica — Empresa Ficticia, Banco Pacífico Digital

Paso 2: `{"bankCode": "PACIFICO", "personType": "JURIDICA", "documentType": "NIT", "documentNumber": "800000001"}`.
Pasos 3 y 4 contra `/api/v1/bank/PACIFICO/...` con `documentType: "NIT"`, `documentNumber: "800000001"`, clave `1234`.

## 6. Casos de error

Todas las respuestas de error tienen el formato
`{ "timestamp", "status", "error", "message", "path" }`.

| Caso | Request | Dato de prueba | HTTP esperado |
|---|---|---|---|
| Monto negativo | `POST /api/v1/payments` | `"amount": -100` | `400` |
| Monto con más de 2 decimales | `POST /api/v1/payments` | `"amount": 100.123` | `400` |
| Descripción vacía | `POST /api/v1/payments` | `"description": ""` | `400` |
| `returnUrl` no http(s) | `POST /api/v1/payments` | `"returnUrl": "javascript:alert(1)"` | `400` |
| API key inválida | `POST /api/v1/payments` | `X-Api-Key: api-key-que-no-existe` | `401` |
| Sin API key | `POST /api/v1/payments` | sin header `X-Api-Key` | `401` |
| Banco inexistente | `POST /api/v1/payments/{id}/start` | `"bankCode": "BANCO_INEXISTENTE"` | `404` |
| Documento sin cuenta en el banco | `POST /api/v1/payments/{id}/start` | `"documentNumber": "9999999999"` | `404` |
| Enum inválido | `POST /api/v1/payments/{id}/start` | `"documentType": "PASAPORTE"` | `400` |
| Iniciar una transacción ya iniciada/aprobada | `POST /api/v1/payments/{id}/start` | id de una transacción `PENDING` o `APPROVED` | `409` |
| Autorizar una transacción no iniciada | `POST /api/v1/bank/ANDINO/authorize` | id de una transacción `CREATED` | `409` |
| Reautorizar una transacción aprobada | `POST /api/v1/bank/ANDINO/authorize` | mismo body del pago aprobado | `409` |
| Clave bancaria incorrecta | `POST /api/v1/bank/ANDINO/authenticate` | `"password": "0000"` | `401` |
| Documento distinto al de la transacción | `POST /api/v1/bank/ANDINO/authenticate` | `"documentNumber": "1000000002"` en una transacción de Ana | `401` |
| Banco distinto al de la transacción | `POST /api/v1/bank/CONDOR/authenticate` | transacción iniciada con `ANDINO` | `400` |
| Token de sesión inválido o ya usado | `POST /api/v1/bank/ANDINO/authorize` | `"bankSessionToken": "token-falso"` | `401` |
| Transacción inexistente | `GET /api/v1/payments/{id}` | `00000000-0000-0000-0000-000000000000` | `404` |
| Id con formato inválido | `GET /api/v1/payments/{id}` | `no-es-un-uuid` | `400` |
| Referencia inexistente | `GET /api/v1/payments/reference/{ref}` | `PL-00000000-XXXXXX` | `404` |
| Cuenta inexistente | `GET /api/v1/accounts/{n}/balance` | `0000000000` | `404` |

## 7. Ejemplo rápido con curl (camino feliz)

```bash
BASE=http://localhost:8080

TX=$(curl -s -X POST $BASE/api/v1/payments -H "X-Api-Key: demo-api-key-123" -H "Content-Type: application/json" \
  -d '{"amount":150000.00,"description":"Compra aprobada"}' | sed -E 's/.*"transactionId":"([^"]+)".*/\1/')

curl -s -X POST $BASE/api/v1/payments/$TX/start -H "Content-Type: application/json" \
  -d '{"bankCode":"ANDINO","personType":"NATURAL","documentType":"CC","documentNumber":"1000000001"}'

TOKEN=$(curl -s -X POST $BASE/api/v1/bank/ANDINO/authenticate -H "Content-Type: application/json" \
  -d "{\"transactionId\":\"$TX\",\"documentType\":\"CC\",\"documentNumber\":\"1000000001\",\"password\":\"1234\"}" \
  | sed -E 's/.*"bankSessionToken":"([^"]+)".*/\1/')

curl -s -X POST $BASE/api/v1/bank/ANDINO/authorize -H "Content-Type: application/json" \
  -d "{\"transactionId\":\"$TX\",\"bankSessionToken\":\"$TOKEN\",\"approve\":true}"

curl -s $BASE/api/v1/accounts/1000000001/balance
```

## 8. Flujo por pantallas web

1. Abrir `http://localhost:8080/` (Tienda Demo) → **Pagar con PayLink**.
2. Checkout: banco **Banco Andino**, persona **Natural**, documento **CC 1000000001** → **Ir a mi banco**.
3. Login del banco: clave **1234** → **Ingresar**.
4. **Aprobar pago** o **Rechazar** → comprobante en `/result/{transactionId}`.
