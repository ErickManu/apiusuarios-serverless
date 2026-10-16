# Pruebas de Carga y Análisis de Rendimiento con Grafana k6

## Entrega técnica consolidada

El [informe definitivo PDF](informe/Informe%20De%20Pruebas%20Paralelo%201-21-2841.pdf)
se conserva sin modificaciones e incluye capturas de CloudWatch y consola.
Las actividades 1 y 2 ya se ejecutaron. El [informe técnico](informe/INFORME-TECNICO.md)
y su [resumen visual local](informe/RESUMEN-VISUAL.html) documentan los JSON reales.
Ver [Actividad 4](informe/ACTIVIDAD4-DEGRADACION.md),
[Actividad 5](informe/ACTIVIDAD5-RECUPERACION.md), [índice de evidencias](evidencias/README.md)
y [escenarios pendientes bloqueados](pendientes/README.md).
La cuota de 10 y los 40 throttles de A2 fueron confirmados por el usuario en
CloudWatch; sus exportaciones siguen pendientes. No se realizó estrés incremental
completo ni se midió todavía recuperación. Los comandos originales que siguen
se conservan como documentación; no es necesario repetir las pruebas para el informe.

## Actividad 1: Línea base controlada

| Parámetro | Configuración |
| --- | --- |
| Solicitud | `POST /notifications/send`, JSON con `email`, `subject`, `message` |
| Escenario | `constant-vus`: **10 VUs durante 1 minuto** |
| Destinatario obligatorio | `success@simulator.amazonses.com` |
| Presupuesto por VU | **Máximo 3 POST durante toda su ejecución**, incluidos errores y timeouts |
| Presupuesto total | **Máximo 30 POST por ejecución** |
| Separación | Aproximadamente 20 segundos entre inicios; pausa mínima de 2 segundos |
| Timeout HTTP | 30 segundos |
| Éxito | HTTP 202 sin error de transporte |

**Es una línea base controlada, no una prueba de carga sostenida.** Con respuestas
rápidas, cada VU intenta enviar cerca de los segundos 0, 20 y 40. Tras su tercer
intento solo espera; permanece en el escenario hasta terminar el minuto. Si hay
respuestas lentas o interrupciones, puede haber menos de 30 POST. No sirve para
afirmar la capacidad máxima del sistema ni el rendimiento bajo tráfico continuo.
Los VUs representan concurrencia, no solicitudes por segundo.

El contador reside fuera de la función de iteración y es independiente por VU.
Se incrementa antes del POST. No se reintentan fallos ni se siguen redirecciones.
Además, solo los IDs de VU 1 a 10 pueden enviar y un umbral comprueba que
`http_reqs` esté entre 1 y 30. El límite empieza de nuevo al ejecutar otra vez k6.
No sobrescribir escenarios, VUs, duración, iteraciones ni opciones de resumen
mediante otras banderas o variables `K6_*` de otra práctica.

El escenario dura 60 segundos y permite hasta 35 segundos adicionales para
finalizar iteraciones en curso, sin iniciar nuevas. El cupo también se aplica
durante ese margen.

## Preparación en PowerShell para Windows

Desde la raíz del repositorio, con k6 en `PATH`:

```powershell
Set-Location .\pruebas-carga
k6 version
$env:NOTIFICATIONS_URL = Read-Host 'URL completa del endpoint de pruebas, terminada en /notifications/send'
$env:TEST_EMAIL = 'success@simulator.amazonses.com'
$jwtSeguro = Read-Host 'JWT válido de pruebas, sin el prefijo Bearer' -AsSecureString
$env:NOTIFICATIONS_TOKEN = [System.Net.NetworkCredential]::new('', $jwtSeguro).Password
Remove-Variable jwtSeguro
```

`NOTIFICATIONS_URL` incluye protocolo, host, stage cuando corresponda y la ruta.
El script no agrega `/notifications/send`. Obtener la base desde el Invoke URL
existente o el output `api_base_url`, sin aplicar Terraform. No colocar
credenciales dentro de la URL. No se incluye una URL real en estos archivos.

El JWT se mantiene en una variable de entorno de la sesión; el script agrega
`Authorization: Bearer ...`. El prompt oculta su escritura y el valor no forma
parte del comando ni del resumen JSON. No imprimir las variables, activar
`--http-debug`/`K6_HTTP_DEBUG` ni guardar una transcripción del token.

**Validación obligatoria:** si `TEST_EMAIL` falta o no es exactamente el
simulador indicado, el script falla al inicializar, antes de enviar solicitudes.
También exige la URL completa y el JWT. Es una comprobación local del
destinatario: no consulta AWS. La identidad remitente, permisos y cuotas de SES
deben seguir vigentes.

El simulador conserva el flujo real **API Gateway → Lambda Java → SNS → SQS →
Lambda Python → SES**, sin entregar mensajes a Gmail. SES sigue aplicando cargos
y límites de tasa, aunque el simulador no consume la cuota diaria de envío a
destinatarios normales. El límite de 30 cubre los POST de k6; no limita
reintentos o duplicados internos de SNS/SQS/Lambda/SES.

## Comando de la actividad

Cuando decidas ejecutar la actividad, después de la preparación:

```powershell
$marca = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
k6 run --no-usage-report -e SINGLE_REQUEST=false --summary-export ".\evidencias\actividad1-$marca.json" .\actividad1-linea-base.js
$codigoActividad = $LASTEXITCODE
Write-Host "Código de salida de k6: $codigoActividad"
```

El nombre incluye fecha y hora para conservar las ejecuciones anteriores.
`evidencias/` ya existe. `--summary-export` guarda el resumen agregado JSON,
con métricas y checks; no guarda cabeceras, JWT ni cuerpos de mensajes. No usar
`--out json=...` como sustituto: es una serie de muestras distinta del resumen.
Preparar el script no genera resultados nuevos.

Opcionalmente, para conservar también el diagnóstico técnico en texto, sustituir
la línea de `k6 run` anterior por esta; **no ejecutar ambas**, porque cada
ejecución tiene su propio presupuesto de hasta 30 POST:

```powershell
k6 run --no-usage-report --no-color -e SINGLE_REQUEST=false --summary-export ".\evidencias\actividad1-$marca.json" .\actividad1-linea-base.js 2>&1 | Tee-Object -FilePath ".\evidencias\actividad1-$marca-consola.txt"
```

## Modo de una sola solicitud

Se conserva `SINGLE_REQUEST=true`: **1 VU, 1 iteración y máximo 1 POST**, con el
mismo bloqueo de destinatarios. No es necesario repetir la verificación ya
exitosa, salvo que cambie el entorno o el token.

```powershell
$marca = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
k6 run --no-usage-report -e SINGLE_REQUEST=true --summary-export ".\evidencias\verificacion-$marca.json" .\actividad1-linea-base.js
```

## Resultados

| Métrica solicitada | Campo del resumen |
| --- | --- |
| Tiempo promedio, mínimo y máximo | `http_req_duration`: `avg`, `min`, `max` (ms) |
| p95 y p99 | `http_req_duration`: `p(95)`, `p(99)` (ms) |
| Throughput | Tasa `/s` de `http_reqs`; incluye intentos exitosos y fallidos |
| Cantidad de solicitudes | Total de `http_reqs`: entre 1 y 30 en la actividad |
| Porcentaje de errores | Porcentaje de `http_req_failed` |
| Validación | Check `HTTP 202: notificación aceptada` |

Los umbrales exigen cero errores, todos los checks aprobados y respetar el total
permitido; si fallan, k6 termina con código distinto de cero. No se impone un
objetivo de latencia inventado. Con tan pocas muestras, p95 y p99 son descriptivos
y poco estables: no demuestran el comportamiento bajo carga alta.

El throughput incluye las pausas y el tiempo de espera del escenario. Como
referencia matemática, 30 POST / 60 s son 0,5 POST/s; no es un resultado medido.
La tasa del resumen puede variar por la duración real y el margen final.
`http_req_duration` excluye pausas y no mide el tiempo total de SNS, SQS y el
consumidor. No sustituirlo por `iteration_duration`, que sí incluye las esperas.
No mezclar resultados fríos y calientes sin indicarlo.

Cada POST registra únicamente `vu`, `post_number`, `http_status`, `k6_error_code`,
`connection_error` y `duration_ms`. El estado HTTP 0 indica que no se recibió
un estado válido. El error de transporte se clasifica sin imprimir su texto
original. Un timeout consume cupo y no demuestra que AWS dejara de procesar.

## Confirmación del procesamiento asíncrono

HTTP 202 confirma aceptación de SNS por el backend. Revisar después las métricas
y registros indicados en [REVISION-ASINCRONA.md](REVISION-ASINCRONA.md).
Preparar estos archivos no consulta AWS ni confirma resultados nuevos.
Conservar los archivos previos de `evidencias/`.

Al finalizar la sesión:

```powershell
Remove-Item Env:NOTIFICATIONS_URL, Env:TEST_EMAIL, Env:NOTIFICATIONS_TOKEN -ErrorAction SilentlyContinue
```

Referencias: [estadísticas k6](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/#summary-trend-stats),
[exportación del resumen](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/#summary-export),
[constant-vus](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-vus/)
y [simulador SES](https://docs.aws.amazon.com/ses/latest/dg/send-an-email-from-console.html#send-email-simulator).
