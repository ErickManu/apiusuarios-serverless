import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

// Estos escenarios son entregables pendientes, no pruebas autorizadas.
// No existe una variable de entorno que permita eludir el bloqueo.
export function bloquearPorCuota(vus) {
  throw new Error(`PENDIENTE: escenario de ${vus} VUs bloqueado. Cuota regional confirmada: 10 ejecuciones simultáneas. No se enviaron solicitudes.`);
}

export function crearEscenarioPendiente(vus) {
  if (![100, 250, 500].includes(vus)) {
    throw new Error('Escenario pendiente no reconocido.');
  }
  let postAttempted = false;
  const options = {
    scenarios: {
      incremento_pendiente: {
        executor: 'constant-vus', vus, duration: '2m', gracefulStop: '35s',
      },
    },
    maxRedirects: 0,
    discardResponseBodies: true,
    summaryTrendStats: ['avg', 'min', 'max', 'p(95)', 'p(99)'],
    summaryTimeUnit: 'ms',
    thresholds: {
      http_req_failed: ['rate==0'], checks: ['rate==1'],
      http_reqs: ['count>0', `count<=${vus}`],
    },
  };

  function ejecutar() {
    // Segunda barrera: aun quitando el bloqueo de init, no se realiza HTTP.
    bloquearPorCuota(vus);

    // Diseño conservador para una revisión futura, actualmente inalcanzable.
    if (postAttempted || exec.vu.idInTest < 1 || exec.vu.idInTest > vus) {
      sleep(1);
      return;
    }
    const url = (__ENV.NOTIFICATIONS_URL || '').trim();
    const email = (__ENV.TEST_EMAIL || '').trim();
    const token = (__ENV.NOTIFICATIONS_TOKEN || '').trim();
    if (email !== 'success@simulator.amazonses.com') {
      throw new Error('Solo se permite el simulador de SES.');
    }
    if (!/^https?:\/\/[^/@\s?#]+(?:\/[^\s?#]*)?\/notifications\/send\/?$/.test(url)
        || !token || /[<>\r\n]/.test(token) || /^Bearer\s/i.test(token)) {
      throw new Error('Configuración de pruebas incompleta o inválida.');
    }
    if (__ENV.K6_HTTP_DEBUG || exec.test.options.httpDebug) {
      throw new Error('El diagnóstico HTTP detallado está bloqueado.');
    }
    postAttempted = true;
    const response = http.post(url, JSON.stringify({
      email, subject: `Actividad 2 pendiente: ${vus} VUs`, message: 'Prueba controlada.',
    }), {
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      timeout: '30s', redirects: 0,
      responseCallback: http.expectedStatuses(202),
      tags: { name: `POST /notifications/send - pendiente-${vus}` },
    });
    check(response, { 'HTTP 202 sin error': r => r.status === 202 && !r.error });
    // Omitir cabeceras, JWT, URL, destinatario, cuerpo y error sin filtrar.
    console.log(JSON.stringify({
      vu: exec.vu.idInTest, http_status: response.status,
      k6_error_code: response.error_code || 0, duration_ms: response.timings.duration,
    }));
    sleep(2);
  }
  return { options, ejecutar };
}
