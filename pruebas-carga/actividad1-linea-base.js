import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const SES_SIMULATOR_EMAIL = 'success@simulator.amazonses.com';
const BASELINE_VUS = 10;
const BASELINE_MAX_POSTS_PER_VU = 3;
const BASELINE_INTERVAL_SECONDS = 20;

// Validar la configuración antes de realizar cualquier solicitud.
function requiredEnv(name) {
  const value = (__ENV[name] || '').trim();
  if (!value || /[<>]/.test(value)) {
    throw new Error(`Configura ${name} con un valor de tu entorno de pruebas.`);
  }
  return value;
}

const url = requiredEnv('NOTIFICATIONS_URL');
const email = requiredEnv('TEST_EMAIL');
const token = requiredEnv('NOTIFICATIONS_TOKEN');
const singleRequest = __ENV.SINGLE_REQUEST === 'true';
const allowedVus = singleRequest ? 1 : BASELINE_VUS;
const maxPostsPerVu = singleRequest ? 1 : BASELINE_MAX_POSTS_PER_VU;

if (!/^https?:\/\/[^/@\s?#]+(?:\/[^\s?#]*)?\/notifications\/send\/?$/.test(url)) {
  throw new Error('NOTIFICATIONS_URL debe ser la URL completa terminada en /notifications/send, incluido el stage si corresponde.');
}
if (email !== SES_SIMULATOR_EMAIL) {
  throw new Error('Prueba bloqueada: TEST_EMAIL debe ser success@simulator.amazonses.com.');
}
if (__ENV.SINGLE_REQUEST && !['true', 'false'].includes(__ENV.SINGLE_REQUEST)) {
  throw new Error('SINGLE_REQUEST solo admite true o false.');
}

export const options = {
  // Cada iteración permitida contiene un POST; después del cupo solo se espera.
  scenarios: {
    notifications: {
      ...(singleRequest
        ? { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '45s' }
        : { executor: 'constant-vus', vus: BASELINE_VUS, duration: '1m' }),
      // Dar margen al timeout de 30 s y a la pausa final.
      gracefulStop: '35s',
    },
  },
  maxRedirects: 0,
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'max', 'p(95)', 'p(99)'],
  summaryTimeUnit: 'ms',
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    // Comprobación adicional del total; el bloqueo efectivo está antes del POST.
    http_reqs: ['count>0', `count<=${allowedVus * maxPostsPerVu}`],
  },
};

// El contrato actual de NotificationController devuelve 202 Accepted.
http.setResponseCallback(http.expectedStatuses(202));

const payload = JSON.stringify({
  email,
  subject: singleRequest ? 'Práctica k6 - verificación única' : 'Práctica k6 - línea base',
  message: 'Mensaje de prueba para la práctica universitaria de rendimiento.',
});

// k6 mantiene un estado JavaScript separado para cada VU. No reiniciar este
// contador dentro de default(): el cupo es para toda la ejecución de ese VU.
let postsSent = 0;

function connectionError(response) {
  // No registrar response.error directamente: puede incluir URL u otros datos.
  if (response.error_code === 1050 || response.error_code === 1211) {
    return 'timeout';
  }
  if (response.error || response.status === 0) {
    return 'error_de_transporte';
  }
  return null;
}

export default function () {
  // No abandonar el escenario al alcanzar el cupo: conservar los VUs activos.
  // El filtro de ID también impide enviar desde VUs adicionales por accidente.
  if (exec.vu.idInTest > allowedVus || postsSent >= maxPostsPerVu) {
    sleep(1);
    return;
  }

  // Reservar el intento ANTES de enviarlo: errores y timeouts consumen cupo.
  postsSent += 1;
  const startedAt = Date.now();

  const response = http.post(url, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    // Ampliado para diagnosticar respuestas lentas, sin reintentar el POST.
    timeout: '30s',
    tags: { name: 'POST /notifications/send' },
  });

  check(response, {
    'HTTP 202: notificación aceptada': (result) => result.status === 202 && !result.error,
  });

  // A lo sumo 30 registros; solo metadatos técnicos, sin URL ni datos privados.
  console.log(JSON.stringify({
    vu: exec.vu.idInTest,
    post_number: postsSent,
    http_status: response.status,
    k6_error_code: response.error_code || 0,
    connection_error: connectionError(response),
    duration_ms: response.timings.duration,
  }));

  // Aproximadamente t=0, 20 y 40 s con respuestas rápidas. Si son lentas, puede
  // haber menos de 3 POST por VU. Siempre descansar al menos 2 s, incluso al fallar.
  const elapsedSeconds = (Date.now() - startedAt) / 1000;
  sleep(singleRequest ? 2 : Math.max(2, BASELINE_INTERVAL_SECONDS - elapsedSeconds));
}
