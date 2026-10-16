import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const VUS = 50;
const SES_SIMULATOR_EMAIL = 'success@simulator.amazonses.com';

// Son categorías de diagnóstico; no afirman qué códigos tuvo la prueba anterior.
// El código exacto siempre se conserva en el registro técnico por solicitud.
const HTTP_STATUS_GROUPS = [
  '0', '200', '201', '202', '204', '400', '401', '403', '404', '405',
  '408', '413', '415', '422', '429', '500', '502', '503', '504', 'otros',
];
const responsesByStatus = new Counter('http_respuestas_por_estado');
const statusThresholds = {};
for (const status of HTTP_STATUS_GROUPS) {
  // Declarar las submétricas para incluir los grupos en el resumen exportado.
  // count>=0 solo habilita su observación; no cambia el criterio de éxito HTTP.
  statusThresholds[`http_respuestas_por_estado{status:${status}}`] = ['count>=0'];
}

function requiredEnv(name) {
  const value = (__ENV[name] || '').trim();
  if (!value || /[<>]/.test(value)) {
    throw new Error(`Configura ${name} con un valor de tu entorno de pruebas.`);
  }
  return value;
}

// No hay llamadas de preparación ni de autenticación en este script.
const url = requiredEnv('NOTIFICATIONS_URL');
const email = requiredEnv('TEST_EMAIL');
const token = requiredEnv('NOTIFICATIONS_TOKEN');

if (!/^https?:\/\/[^/@\s?#]+(?:\/[^\s?#]*)?\/notifications\/send\/?$/.test(url)) {
  throw new Error('NOTIFICATIONS_URL debe ser la URL completa terminada en /notifications/send, sin credenciales ni parámetros.');
}
if (email !== SES_SIMULATOR_EMAIL) {
  throw new Error('Prueba bloqueada: TEST_EMAIL debe ser success@simulator.amazonses.com.');
}
if (/^Bearer\s/i.test(token) || /[\r\n]/.test(token)) {
  throw new Error('NOTIFICATIONS_TOKEN debe contener solo el JWT, sin Bearer ni saltos de línea.');
}
if (__ENV.K6_HTTP_DEBUG && __ENV.K6_HTTP_DEBUG !== 'false') {
  throw new Error('Prueba bloqueada: desactiva K6_HTTP_DEBUG para proteger las credenciales.');
}

export const options = {
  scenarios: {
    actividad2_50vus: {
      executor: 'constant-vus',
      vus: VUS,
      duration: '2m',
      gracefulStop: '35s',
    },
  },
  maxRedirects: 0,
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'max', 'p(95)', 'p(99)'],
  summaryTimeUnit: 'ms',
  thresholds: {
    ...statusThresholds,
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    // Verificación adicional al terminar; el bloqueo real está antes del POST.
    http_reqs: ['count>0', `count<=${VUS}`],
  },
};

http.setResponseCallback(http.expectedStatuses(202));

const payload = JSON.stringify({
  email,
  subject: 'Práctica k6 - Actividad 2 - escenario de 50 VUs',
  message: 'Mensaje de prueba controlada: un intento por usuario virtual.',
});

// k6 mantiene este estado separado por VU y lo conserva entre iteraciones.
// No restablecerlo dentro de default(): el cupo es para toda la ejecución.
let postAttempted = false;

function connectionError(response) {
  if (response.error_code === 1050 || response.error_code === 1211) {
    return 'timeout';
  }
  if (response.error || response.status === 0) {
    return 'error_de_transporte';
  }
  return null;
}

export default function () {
  // También bloquear --http-debug si aparece en las opciones efectivas.
  if (exec.test.options.httpDebug) {
    exec.test.abort('Prueba bloqueada: desactiva http-debug para proteger las credenciales.');
    return;
  }

  // Incluso si se crean más VUs por accidente, solo los IDs 1 a 50 envían.
  // Una vez consumido el cupo, esperar conserva el escenario de dos minutos.
  if (exec.vu.idInTest < 1 || exec.vu.idInTest > VUS || postAttempted) {
    sleep(1);
    return;
  }

  // Reservar el único intento ANTES del envío: un error, timeout o excepción
  // no habilita otra solicitud en la siguiente iteración. No hay reintentos.
  postAttempted = true;

  const response = http.post(url, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    timeout: '30s',
    redirects: 0,
    tags: { name: 'POST /notifications/send - actividad2-50vus' },
  });

  const httpStatus = String(response.status);
  const statusGroup = HTTP_STATUS_GROUPS.includes(httpStatus) ? httpStatus : 'otros';
  responsesByStatus.add(1, { status: statusGroup });

  check(response, {
    'HTTP 202: notificación aceptada': (result) => result.status === 202 && !result.error,
  });

  // No imprimir response.error, cabeceras, URL, destinatario, cuerpo ni JWT.
  console.log(JSON.stringify({
    timestamp_utc: new Date().toISOString(),
    vu: exec.vu.idInTest,
    post_number: 1,
    http_status: response.status,
    status_group: statusGroup,
    k6_error_code: response.error_code || 0,
    connection_error: connectionError(response),
    duration_ms: response.timings.duration,
  }));

  sleep(2);
}
