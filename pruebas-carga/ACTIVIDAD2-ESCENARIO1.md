# Actividad 2: Incremento Progresivo — primer escenario

## Configuración preparada

| Parámetro | Valor |
| --- | --- |
| Script | `actividad2-escenario1-50vus.js` |
| Escenario | `constant-vus`, 50 VUs durante 2 minutos |
| Solicitud | `POST /notifications/send` |
| JSON enviado | `email`, `subject`, `message` |
| Destinatario obligatorio | `success@simulator.amazonses.com` |
| Autenticación | JWT en `NOTIFICATIONS_TOKEN`, sin el prefijo Bearer |
| Presupuesto | Un intento POST por VU; máximo 50 por ejecución |
| Timeout HTTP | 30 segundos |
| Respuesta esperada | HTTP 202, sin error de transporte |

**Es una prueba controlada, no carga sostenida de 50 usuarios durante dos
minutos.** Los 50 VUs intentan enviar una vez al comenzar. Luego esperan sin
enviar más, mientras el escenario permanece activo hasta completar 120 segundos.
Esto produce una ráfaga inicial de hasta 50 solicitudes, no una rampa interna.
El incremento respecto de la Actividad 1 es entre escenarios separados. Los
[escenarios de 100, 250 y 500 VUs](pendientes/README.md) están preparados,
bloqueados y no ejecutados por la cuota regional de 10.

## Límites y controles de seguridad

El estado de cada VU registra que su intento ya fue consumido **antes** de llamar
a `http.post`. Errores, timeouts y excepciones también consumen ese intento.
Las siguientes iteraciones solo esperan. Solo los IDs de VU 1 a 50 pueden
enviar; VUs adicionales por una configuración accidental no agregan solicitudes.
No hay login, llamadas de preparación, reintentos ni peticiones auxiliares.
Las redirecciones se deshabilitan globalmente y también en el POST.

El umbral `http_reqs` exige más de cero solicitudes y un máximo de 50. Es una
comprobación final adicional: el límite preventivo es el estado por VU. Los
otros umbrales requieren cero errores y todos los checks aprobados. No se exige
obtener exactamente 50 si la ejecución se interrumpe o no llegan a enviar todos
los VUs. Los límites se reinician en cada nueva ejecución del comando.

Si faltan URL, destinatario o token, el script se detiene antes de hacer HTTP.
También bloquea destinatarios distintos del simulador, URLs con credenciales
o parámetros, JWT con prefijo Bearer/saltos de línea y el diagnóstico HTTP
detallado detectado en las variables u opciones. No activar `--http-debug`.
Esta validación local no consulta AWS ni comprueba cuotas o permisos remotos.

**El máximo de 50 se refiere a POST de k6.** No limita duplicados o reintentos
internos de SNS/SQS/Lambda/SES. SES Simulator evita llenar una bandeja real,
pero conserva consumo AWS y límites de tasa. Una ráfaga de 50 puede producir
espera en SQS o errores de cuota, aunque el volumen total sea pequeño.
Un timeout no demuestra que AWS haya dejado de procesar el mensaje.

El escenario permite hasta 35 segundos adicionales para finalizar iteraciones
en curso, sin iniciar nuevas; el presupuesto sigue vigente durante ese margen.
No cambiar escenarios, VUs, duración ni iteraciones con otras opciones o
variables `K6_*`. El script es independiente del modo `SINGLE_REQUEST` de la
Actividad 1: esa variable no cambia este escenario.

## Preparación en PowerShell

Desde la raíz del repositorio, con k6 instalado en `PATH`:

```powershell
Set-Location .\pruebas-carga
$env:NOTIFICATIONS_URL = Read-Host 'URL completa del entorno de pruebas, terminada en /notifications/send'
$env:TEST_EMAIL = 'success@simulator.amazonses.com'
$jwtSeguro = Read-Host 'JWT válido de pruebas, sin Bearer' -AsSecureString
$env:NOTIFICATIONS_TOKEN = [System.Net.NetworkCredential]::new('', $jwtSeguro).Password
Remove-Variable jwtSeguro
```

La URL debe incluir protocolo, host, stage si corresponde y la ruta completa.
No se proporciona una URL real ni un token en los archivos. Si URL y JWT ya
están configurados y siguen vigentes, se pueden conservar esas variables.
No imprimir el entorno, guardar el token en archivos ni copiarlo en el comando.

## Comando para una ejecución futura

La ejecución histórica ya terminó con 10 aceptadas y 40 fallidas de 50 POST.
No repetirla para preparar la entrega. Este comando conserva la documentación
para una futura ejecución expresamente decidida:

```powershell
$marca = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$resultado = ".\evidencias\actividad2-50vus-$marca.json"
$consola = ".\evidencias\actividad2-50vus-$marca-consola.log"
if ((Test-Path -LiteralPath $resultado) -or (Test-Path -LiteralPath $consola)) { throw 'Un archivo de resultados ya existe. Genera una nueva marca de tiempo.' }
k6 run --no-usage-report --summary-export $resultado --console-output $consola .\actividad2-escenario1-50vus.js
$codigoActividad2 = $LASTEXITCODE
Write-Host "Código de salida de k6: $codigoActividad2"
```

El resumen agregado JSON se crea al finalizar en `evidencias/`. El prefijo
identifica esta actividad; la fecha/hora y la comprobación de existencia evitan
reutilizar archivos anteriores. No se crean resultados al preparar estos archivos.
No ejecutar varias copias simultáneas: cada proceso tendría su propio cupo de 50.

`--console-output` conserva los registros técnicos de `console.log` en un archivo
separado; `--summary-export` no los incorpora al resumen. Es una preparación
para una ejecución futura, no una instrucción para repetir ahora la prueba fallida.

El registro técnico solo contiene `timestamp_utc`, `vu`, `post_number`,
`http_status`, `status_group`, `k6_error_code`, `connection_error` y `duration_ms`.
No registra URL, JWT,
cabeceras, destinatario, cuerpo ni el texto original del error de conexión.
El resumen JSON contiene métricas/checks agregados, no credenciales o payloads.

## Métricas e interpretación

| Dato | Resumen k6 |
| --- | --- |
| Throughput | `http_reqs`: tasa `/s` y total de solicitudes |
| Latencia promedio, mínima y máxima | `http_req_duration`: `avg`, `min`, `max` (ms) |
| P95 y P99 | `http_req_duration`: `p(95)`, `p(99)` (ms) |
| Porcentaje de errores | `http_req_failed` |
| Respuestas esperadas | Check `HTTP 202: notificación aceptada` |
| Cantidad por estado | `http_respuestas_por_estado{status:CODIGO}`: `count` |

El contador adicional separa los estados 0, 200, 201, 202, 204, 400, 401, 403,
404, 405, 408, 413, 415, 422, 429, 500, 502, 503 y 504. Los demás se agrupan en
`otros`, conservando su código exacto en la consola. El estado 0 significa que
no hubo estado HTTP válido; no es un código enviado por el servidor.
Los umbrales `count>=0` de esos grupos sirven para exponer las submétricas en el
resumen; no convierten errores en éxitos. Si un grupo sin muestras no aparece
en una versión de k6, interpretarlo como cero solo cuando la ejecución y el
resto del resumen se completaron. El JSON de una ejecución anterior no adquiere
este desglose al actualizar el script.

Para el diagnóstico de la ejecución ya realizada y consultas de registros
existentes, ver [DIAGNOSTICO-ACTIVIDAD2.md](DIAGNOSTICO-ACTIVIDAD2.md).

Para consultar únicamente los conteos de un resumen futuro, después de que
exista `$resultado` (lectura local, sin solicitudes):

```powershell
$resumen = Get-Content -Raw -LiteralPath $resultado | ConvertFrom-Json
$resumen.metrics.PSObject.Properties | Where-Object { $_.Name -like 'http_respuestas_por_estado{*' } | ForEach-Object {
    $conteo = $_.Value.count
    if ($null -eq $conteo) { $conteo = $_.Value.values.count }
    [pscustomobject]@{ Grupo = $_.Name; Solicitudes = $conteo }
} | Format-Table -AutoSize
```

En el JSON anterior no aparecerán esos grupos: no interpretar su ausencia
histórica como cero errores de un código determinado.

El throughput global incluye el periodo de espera sin nuevos POST. No equivale
a la capacidad máxima del sistema ni al ritmo instantáneo de la ráfaga inicial.
Con 50 muestras, P95/P99 son descriptivos y sensibles a pocos valores extremos.
No sustituir la latencia HTTP por `iteration_duration`: muchas iteraciones solo
esperan. `iterations` puede superar 50 sin que eso signifique más solicitudes;
revisar `http_reqs` para el total de HTTP. Un código de salida distinto de cero
requiere revisar checks, umbrales y diagnóstico antes de repetir el comando.

HTTP 202 confirma aceptación del backend/SNS, no la finalización del consumidor.
Aplicar [REVISION-ASINCRONA.md](REVISION-ASINCRONA.md) a la nueva ventana de prueba:
revisar SNS, pendientes/borrados/DLQ de SQS y los registros de procesamiento SES
del consumidor. Los fallos parciales pueden existir aunque Lambda tenga
`Errors = 0`. Guardar las evidencias de esta actividad por separado y anotar
arranques en frío/caliente, versión de k6 y fecha/zona horaria.

Al terminar la sesión:

```powershell
Remove-Item Env:NOTIFICATIONS_URL, Env:TEST_EMAIL, Env:NOTIFICATIONS_TOKEN -ErrorAction SilentlyContinue
```

Los archivos de la Actividad 1 y sus resultados no se modifican. No se cambia
Terraform, backend ni infraestructura AWS para preparar este escenario.

Referencias oficiales: [constant-vus](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-vus/),
[estado de ejecución y VUs](https://grafana.com/docs/k6/latest/javascript-api/k6-execution/)
y [opciones de resumen](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/#summary-trend-stats).
