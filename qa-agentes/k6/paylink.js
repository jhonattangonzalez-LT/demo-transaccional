// Prueba de carga y funcional de PayLink con k6.
// Uso:  TIPO=smoke|carga|estres  k6 run k6/paylink.js
// Los checks son las pruebas funcionales (cada uno sale como correcto o fallido en el informe).
import http from 'k6/http';
import { check } from 'k6';
// k6 no importa JSON como módulo: se abre y se parsea en el contexto de inicio.
const config = JSON.parse(open('../apis/paylink/config.json'));

const TIPO = __ENV.TIPO || 'smoke';
// La URL se puede cambiar sin tocar el archivo: BASE_URL=https://otra-url k6 run k6/paylink.js
const BASE = (__ENV.BASE_URL || config.base_url).replace(/\/$/, '');
const KEY = { headers: { 'Content-Type': 'application/json', 'X-Api-Key': config.api_key } };
const SIN_KEY = { headers: { 'Content-Type': 'application/json' } };
const FECHA = new Date().toISOString().slice(0, 10);
const INFORME = `informes/carga/${FECHA}-paylink-${TIPO}`;

const cargas = {
  smoke: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '2m' },
  // Corta: para la demo en vivo del coordinador (5 usuarios, ~45 s).
  corta: {
    executor: 'ramping-vus',
    stages: [
      { duration: '10s', target: 5 },
      { duration: '30s', target: 5 },
      { duration: '5s', target: 0 },
    ],
  },
  carga: {
    executor: 'ramping-vus',
    stages: [
      { duration: '30s', target: 10 },
      { duration: '2m', target: 10 },
      { duration: '20s', target: 0 },
    ],
  },
  estres: {
    executor: 'ramping-vus',
    stages: [
      { duration: '30s', target: 10 },
      { duration: '1m', target: 25 },
      { duration: '1m', target: 50 },
      { duration: '20s', target: 0 },
    ],
  },
};

export const options = {
  scenarios: { [TIPO]: { ...cargas[TIPO], exec: 'flujo' } },
  thresholds: {
    http_req_duration: [`p(95)<${config.umbral_p95_ms}`],
    http_req_failed: ['rate<0.01'],
  },
};

const json = (r) => {
  try { return r.json(); } catch (e) { return {}; }
};

// Se ejecuta una vez: devuelve los datos de prueba a su estado inicial.
export function setup() {
  if (config.reset_al_inicio) {
    http.post(`${BASE}/api/v1/demo/reset`, null, KEY);
  }
}

// Cada paso tiene UNA validación con nombre claro. `verificar=false` se usa cuando el paso solo prepara otro.
function crearOrden(monto, descripcion, verificar) {
  const r = http.post(
    `${BASE}/api/v1/payments`,
    JSON.stringify({ amount: monto, description: descripcion, returnUrl: 'https://tienda-demo.example/retorno' }),
    KEY,
  );
  if (verificar) check(r, { '1. Crear la orden de pago (queda CREADA)': (x) => x.status === 201 && json(x).status === 'CREATED' });
  return json(r).transactionId;
}

function iniciarPago(id, verificar) {
  const p = config.pagador;
  const r = http.post(
    `${BASE}/api/v1/payments/${id}/start`,
    JSON.stringify({ bankCode: p.banco, personType: p.tipo_persona, documentType: p.tipo_documento, documentNumber: p.documento }),
    SIN_KEY,
  );
  if (verificar) check(r, { '2. Elegir banco e iniciar el pago (queda PENDIENTE)': (x) => x.status === 200 && json(x).status === 'PENDING' });
}

function autenticar(id, verificar) {
  const p = config.pagador;
  const r = http.post(
    `${BASE}/api/v1/bank/${p.banco}/authenticate`,
    JSON.stringify({ transactionId: id, documentType: p.tipo_documento, documentNumber: p.documento, password: p.clave }),
    SIN_KEY,
  );
  if (verificar) check(r, { '3. Iniciar sesión en el banco (entrega token)': (x) => x.status === 200 && !!json(x).bankSessionToken });
  return json(r).bankSessionToken;
}

function autorizar(id, token, aprobar) {
  const p = config.pagador;
  return http.post(
    `${BASE}/api/v1/bank/${p.banco}/authorize`,
    JSON.stringify({ transactionId: id, bankSessionToken: token, approve: aprobar }),
    SIN_KEY,
  );
}

function pagoAprobado(monto, verificarSaldos) {
  const id = crearOrden(monto, 'Compra aprobada', true);
  if (!id) return;
  iniciarPago(id, true);
  const token = autenticar(id, true);
  if (!token) return;
  check(autorizar(id, token, true), {
    '4. Aprobar el pago (queda APROBADO)': (x) => x.status === 200 && json(x).status === 'APPROVED',
  });
  if (verificarSaldos) {
    const saldoPagador = json(http.get(`${BASE}/api/v1/accounts/${config.cuenta_pagador}/balance`)).balance;
    const saldoComercio = json(http.get(`${BASE}/api/v1/accounts/${config.cuenta_comercio}/balance`)).balance;
    check(null, {
      '5. Saldos correctos (se descuenta al pagador y se abona al comercio)': () =>
        saldoPagador === config.saldo_inicial_pagador - monto && saldoComercio === config.saldo_inicial_comercio + monto,
    });
  }
}

function pagoRechazado(monto) {
  const id = crearOrden(monto, 'Compra rechazada', false);
  if (!id) return;
  iniciarPago(id, false);
  const token = autenticar(id, false);
  if (!token) return;
  check(autorizar(id, token, false), {
    '6. Rechazar un pago (queda RECHAZADO)': (x) =>
      x.status === 200 && json(x).status === 'REJECTED' && json(x).statusReason === 'USER_CANCELLED',
  });
}

export function flujo() {
  if (TIPO === 'smoke') {
    pagoAprobado(config.monto_smoke, true);
    pagoRechazado(config.monto_rechazo);
  } else {
    // En carga: 3 de cada 4 pagos se aprueban y 1 de cada 4 se rechaza. Montos pequeños para no agotar el saldo.
    if (__ITER % 4 === 3) pagoRechazado(config.monto_carga);
    else pagoAprobado(config.monto_carga, false);
  }
}

// Informe visual: HTML con metricas y la lista de checks (cada prueba funcional, pasa o falla).
export function handleSummary(data) {
  const m = data.metrics;
  const v = (nombre, campo) => (m[nombre] && m[nombre].values[campo] !== undefined ? m[nombre].values[campo] : null);
  const fmt = (x, d = 2) => (x === null ? 'n/d' : Number(x).toFixed(d));
  const checks = [];
  const recorrer = (g) => {
    (g.checks || []).forEach((c) => checks.push(c));
    (g.groups || []).forEach(recorrer);
  };
  recorrer(data.root_group);
  const fallos = checks.filter((c) => c.fails > 0).length;
  const filas = checks.map((c) => {
    const total = c.passes + c.fails;
    const pct = total ? ((c.passes / total) * 100).toFixed(1) : '0.0';
    const ok = c.fails === 0;
    return `<tr><td>${c.name}</td><td>${c.passes}</td><td>${c.fails}</td><td>${pct} %</td>` +
      `<td class="${ok ? 'ok' : 'mal'}">${ok ? 'PASA' : 'FALLA'}</td></tr>`;
  }).join('\n');
  const p95 = v('http_req_duration', 'p(95)');
  const umbralOk = p95 !== null && p95 < config.umbral_p95_ms;
  const html = `<!doctype html><html lang="es"><head><meta charset="utf-8">
<title>PayLink · ${TIPO} · ${FECHA}</title>
<style>
body{font-family:system-ui,sans-serif;margin:2rem;color:#1d2330;background:#f7f8fa}
h1{margin:0 0 .25rem}.sub{color:#667}
.cards{display:flex;gap:1rem;flex-wrap:wrap;margin:1.5rem 0}
.card{background:#fff;border-radius:10px;padding:1rem 1.25rem;min-width:170px;box-shadow:0 1px 3px #0001}
.card b{display:block;font-size:1.5rem}.card span{color:#667;font-size:.85rem}
table{border-collapse:collapse;width:100%;background:#fff;border-radius:10px;overflow:hidden}
th,td{padding:.55rem .8rem;text-align:left;border-bottom:1px solid #eef}
th{background:#eef1f6}.ok{color:#117a3d;font-weight:600}.mal{color:#b42318;font-weight:600}
</style></head><body>
<h1>PayLink · prueba ${TIPO}</h1>
<div class="sub">Fecha ${FECHA} · ${BASE} · umbral P95 &lt; ${config.umbral_p95_ms} ms</div>
<div class="cards">
 <div class="card"><span>Peticiones</span><b>${fmt(v('http_reqs', 'count'), 0)}</b></div>
 <div class="card"><span>Tiempo P95</span><b class="${umbralOk ? 'ok' : 'mal'}">${fmt(p95, 0)} ms</b></div>
 <div class="card"><span>Tiempo promedio</span><b>${fmt(v('http_req_duration', 'avg'), 0)} ms</b></div>
 <div class="card"><span>Errores HTTP</span><b>${fmt(v('http_req_failed', 'rate') * 100, 2)} %</b></div>
 <div class="card"><span>Checks que fallan</span><b class="${fallos ? 'mal' : 'ok'}">${fallos} de ${checks.length}</b></div>
</div>
<h2>Pruebas funcionales (checks)</h2>
<table><tr><th>Prueba</th><th>Pasa</th><th>Falla</th><th>% que pasa</th><th>Resultado</th></tr>
${filas}</table>
<p class="sub">Decisión humana: la persona del equipo revisa este informe.</p>
</body></html>`;
  return {
    stdout: `\nInforme: ${INFORME}.html\n`,
    [`${INFORME}.html`]: html,
    [`${INFORME}.json`]: JSON.stringify(data, null, 2),
  };
}
