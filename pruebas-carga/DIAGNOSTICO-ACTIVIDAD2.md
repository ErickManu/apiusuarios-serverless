# Diagnóstico de la prueba controlada de 50 VUs

> Actualización de entrega, 16 de octubre de 2026: el usuario confirmó cuota
> regional de 10, 40 throttles y máximo de concurrencia de 10 en CloudWatch.
> La saturación de concurrencia queda respaldada por esa confirmación; falta
> adjuntar su exportación/captura. Los códigos exactos de las 40 fallidas siguen
> desconocidos. Ver el [informe consolidado](informe/INFORME-TECNICO.md).
> El análisis de hipótesis que sigue se conserva como registro histórico previo
> a esa confirmación; no representa una nueva ejecución ni nuevos códigos HTTP.

## Qué sabemos y qué no sabemos

El resumen existente `evidencias/actividad2-50vus-20261016-014407.json` registra
50 solicitudes, 10 checks correctos y 40 fallidos (80 %). El promedio global es
5127,91484 ms y el de respuestas esperadas, 25295,20983 ms.

Por diferencia entre los agregados, el promedio del grupo fallido es:

```text
(50 × 5127,91484 − 10 × 25295,20983) / 40 = 86,09109 ms
```

Es un cálculo a partir de mediciones existentes, no una prueba nueva. La espera
de conexión máxima del resumen es de unos 381 ms. El patrón apunta a rechazos
rápidos para muchas solicitudes y trabajo lento para las aceptadas. No encaja
con 40 solicitudes agotando el timeout HTTP de 30 segundos.

**Los códigos HTTP de las 40 fallidas siguen desconocidos.** El JSON anterior
solo tiene estadísticas agregadas y no permite reconstruirlos. La consola
original los imprimía, pero no está guardada en los archivos locales revisados.
No se deben rellenar con 500, 429 ni otros valores supuestos.

La hipótesis principal es capacidad de concurrencia insuficiente durante la
ráfaga, no una causa confirmada. Spring Boot lento puede mantener ocupados los
entornos aceptados y agravar un límite de concurrencia. El mismo JWT se comparte
entre VUs: que 10 sean aceptadas reduce la probabilidad de que el token sea
inválido para todas, pero solo los estados reales permiten descartar 401/403.

## Configuración local revisada, sin modificar AWS

En `terraform/main.tf` y `terraform/notification-lambda.tf` no están declarados
concurrencia reservada/provisionada, límites de tasa/burst del stage, usage plans
ni configuración de acceso/ejecución de logs de API Gateway. El mapping SQS no
declara `maximum_concurrency`. El backend tiene como valores predeterminados
1024 MB y timeout de 28 s; API Gateway tiene integración proxy con timeout 29 s.

La ausencia de concurrencia reservada no significa capacidad ilimitada: el
backend comparte la cuota regional con otras funciones. Los archivos locales
no prueban el valor actual de la cuota ni posibles cambios hechos en consola.
`batch_size=5` del consumidor no es un límite de concurrencia del backend.

## Cómo recuperar códigos desde registros que ya existan

Revisar **us-east-2** y la ventana real de inicio/fin de esta prueba. El nombre
del archivo contiene la hora del equipo que lo generó: comprobar su zona horaria
antes de compararlo con UTC de CloudWatch. No mezclar la verificación individual
ni la Actividad 1 con esta ventana. No generar nuevas solicitudes para investigar.

En API Gateway REST, abrir el stage `dev` y **consultar** Logs/Tracing para ver
si había registros habilitados y cuál era el grupo de acceso. No activar ni
guardar cambios. Si existen logs de ejecución, el nombre habitual del grupo es
`API-Gateway-Execution-Logs_<api-id>/dev`.

Si el grupo de acceso ya contiene JSON con el campo `status`, usar Logs Insights:

```text
fields status
| filter ispresent(status)
| stats count(*) as solicitudes by status
| sort solicitudes desc
```

Limitar primero al grupo/stage y ventana correctos. Agregar filtro POST/ruta
solo si esos campos existen en el formato guardado. Las consultas dependen de
los nombres reales de los campos: no se generan automáticamente campos que no
estuvieran incluidos en el formato de acceso.

Si existen logs de ejecución con el mensaje estándar de cierre:

```text
fields @message
| parse @message /Method completed with status: (?<http_status>[0-9]{3})/
| filter ispresent(http_status)
| stats count(*) as solicitudes by http_status
| sort solicitudes desc
```

La consulta solo devuelve códigos y cantidades. Si el formato es otro, adaptar
el parseo a las líneas existentes sin exportar tokens, cabeceras ni cuerpos.
Buscar también indicios de `TooManyRequestsException`, `Rate Exceeded` y errores
de integración, sin compartir registros completos con datos sensibles.

**Si no había logs de acceso/ejecución y no se conserva la consola, los códigos
individuales no se pueden recuperar de este resumen.** Las métricas 4XX/5XX
permiten distinguir familias, no el código exacto. Habilitar logs después no
reconstruye los registros históricos.

## Qué revisar para confirmar la causa

| Recurso | Métrica/configuración | Interpretación |
| --- | --- | --- |
| Lambda `apiusuarios-dev` | `Throttles` (Sum) | Solicitudes rechazadas por Lambda |
| Misma Lambda | `Invocations`, `Errors` (Sum) | Ejecuciones admitidas y fallos dentro del código/runtime |
| Misma Lambda | `ConcurrentExecutions` (Maximum) | Concurrencia alcanzada durante la ráfaga |
| Cuenta/región | `UnreservedConcurrentExecutions`, `ClaimedAccountConcurrency` (Maximum) y cuota en Service Quotas | Capacidad compartida ocupada frente a la cuota real |
| Lambda/configuración | Concurrencia reservada/provisionada efectiva | Comprobar si difiere del código local, solo mediante consulta |
| API Gateway REST/stage dev | `Count` (SampleCount), `4XXError`/`5XXError` (Sum) | Cantidad y familia de errores |
| API Gateway | `IntegrationLatency`, `Latency` (Average/Maximum) | Tiempo del backend frente al total del gateway |
| Logs de backend | `INIT_REPORT`, `Status: timeout`, errores de Spring/Hikari y timeouts | Arranques repetidos o fallos de inicialización |

Una combinación cercana a 40 throttles, 10 invocaciones y concurrencia máxima
cercana al límite disponible respaldaría rechazo por concurrencia, aislando
otro tráfico. No afirmar que estos valores se observaron: aún no se consultaron.
Las solicitudes limitadas por Lambda no cuentan en `Invocations` ni `Errors`.
API Gateway puede devolver **500** cuando Lambda rechaza la invocación; no
buscar solamente 429. Un 429 del gateway, un 401/403 y un 502/503 requieren
investigaciones distintas.

SES ocurre después de SNS/SQS y no explica directamente un rechazo HTTP rápido
del backend. La Lambda consumidora sí comparte la cuota regional; consultar su
actividad al revisar capacidad total. Para procesamiento posterior, seguir
[REVISION-ASINCRONA.md](REVISION-ASINCRONA.md).

## Registro seguro preparado para una prueba futura

El script ahora añade `http_respuestas_por_estado`, con submétricas de los
estados comunes, 0 (sin estado HTTP) y `otros`. El código exacto permanece en
cada línea técnica de consola. Los grupos se declaran con umbrales neutros para
que entren en el resumen; el check de éxito sigue exigiendo 202 sin error.
El comando actualizado de [ACTIVIDAD2-ESCENARIO1.md](ACTIVIDAD2-ESCENARIO1.md)
guarda tanto el resumen como la consola mediante `--console-output`, con nombres
nuevos. Esta preparación no repite la prueba ni completa datos que faltan.

Los únicos datos del registro propio son fecha UTC, VU, número de intento,
estado HTTP, grupo de estado, código numérico de error, clasificación del error
y duración. Se mantienen el máximo de un POST por VU/50 totales y el simulador.
No se registran JWT, contraseñas, URL, destinatario, cuerpo ni cabeceras.

Un formato mínimo de acceso JSON de API Gateway que podría evaluarse **en una
fase futura autorizada**, sin aplicarlo ahora, es:

```json
{"requestId":"$context.requestId","status":"$context.status","lambdaServiceStatus":"$context.integrationStatus","backendStatus":"$context.integration.status","integrationLatency":"$context.integrationLatency"}
```

`status` es lo que recibe el cliente; `lambdaServiceStatus` corresponde a la API
de Lambda y `backendStatus` al código retornado por el backend proxy. No son
intercambiables. Ese formato omite cabeceras, IP, JWT y cuerpos.

## Antes de aumentar a 100 VUs

Confirmar estados, throttles y cuota efectiva primero. Después, abordar el
arranque síncrono de Spring/JPA y revisar la conexión a Neon, distinguiendo
frío/caliente. Si se confirma falta de concurrencia, evaluar capacidad o una
distribución progresiva de los intentos; no intentar resolverlo con timeouts
mayores o reintentos automáticos. Aún no preparar ni ejecutar 100 VUs.

Referencias oficiales: [errores Lambda/API Gateway](https://docs.aws.amazon.com/lambda/latest/dg/services-apigateway-errors.html),
[métricas Lambda](https://docs.aws.amazon.com/lambda/latest/dg/monitoring-metrics-types.html),
[logs REST API](https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-logging.html),
[campos de acceso](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-variables-for-access-logging.html),
[submétricas por etiquetas k6](https://grafana.com/docs/k6/latest/using-k6/thresholds/#set-thresholds-for-specific-tags)
y [salida de consola k6](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/#console-output).
