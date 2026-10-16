# Evidencias del flujo asíncrono de la Actividad 1

Consultar CloudWatch en **us-east-2**, en la ventana de la prueba y los minutos
posteriores. Anotar inicio/fin y zona horaria; La Paz es UTC-04:00.
Comparar el estado anterior y posterior para separar mensajes previos u otro
tráfico. Los nombres siguientes corresponden a `apiusuarios-dev` según Terraform
local; comprobar que coincidan con los recursos existentes.

No hace falta desplegar infraestructura ni consumir mensajes manualmente.
La confirmación de cada etapa es distinta de la respuesta HTTP de k6.

## SNS: aceptación y entrega a SQS

En `AWS/SNS`, dimensión `TopicName = apiusuarios-dev-notifications`, revisar:

| Métrica | Estadística | Evidencia |
| --- | --- | --- |
| `NumberOfMessagesPublished` | Sum | Mensajes aceptados en el tópico |
| `NumberOfNotificationsDelivered` | Sum | Entregas a suscripciones, incluida SQS |
| `NumberOfNotificationsFailed` | Sum | Fallos definitivos de entrega a SQS |

Contrastar con los POST que obtuvieron 202 y con otras suscripciones/tráfico,
si los hubiera. SNS publica métricas por minuto: no esperar actualización
inmediata ni interpretar entrega a SQS como éxito de SES.

## SQS: recepción, procesamiento y acumulación

En `AWS/SQS`, dimensión `QueueName = apiusuarios-dev-notifications-queue`:

| Métrica | Estadística útil | Evidencia |
| --- | --- | --- |
| `NumberOfMessagesSent` | Sum | Entradas a la cola |
| `NumberOfMessagesReceived` | Sum | Recepciones; puede incluir reintentos |
| `NumberOfMessagesDeleted` | Sum | Borrados; puede contar operaciones repetidas |
| `ApproximateNumberOfMessagesVisible` | Maximum y último valor | Pendientes disponibles |
| `ApproximateNumberOfMessagesNotVisible` | Maximum y último valor | Mensajes en curso |
| `ApproximateAgeOfOldestMessage` | Maximum | Antigüedad del pendiente más viejo |

Después de procesar, los pendientes deberían volver al nivel anterior
(idealmente 0 sin otro tráfico). Revisar también los mensajes visibles en la
DLQ `apiusuarios-dev-notifications-dlq`. Las métricas aproximadas y contadores de
operaciones no son conteos exactos de mensajes únicos. Terraform local configura
visibilidad de 180 segundos y traslado a DLQ tras fallos repetidos: un problema
puede tardar varios minutos en aparecer como reintento o mensaje en DLQ.

## Lambda consumidora: aceptación de SES y fallos parciales

En `AWS/Lambda`, dimensión `FunctionName = apiusuarios-dev-notification-lambda`,
revisar `Invocations`, `Errors` y `Throttles` con Sum; `Duration` con
Average/Maximum y los registros START/END/REPORT. Las invocaciones no equivalen
al número de mensajes: el mapping local permite lotes de hasta 5 mensajes.

En el grupo `/aws/lambda/apiusuarios-dev-notification-lambda`, el código actual
registra `Mensaje SQS ... procesado. SES ID: ...` después del éxito de
`ses.send_email`. Guardar identificadores SQS/SES y hora; no guardar cuerpos,
correos reales, cabeceras ni credenciales.

**`Errors = 0` no basta:** la función captura errores por mensaje y devuelve
`batchItemFailures`, con `ReportBatchItemFailures` habilitado en Terraform.
Buscar también `Error procesando mensaje SQS` y `Tiempo insuficiente`.
Los elementos fallidos pueden reintentarse aunque la invocación termine bien.

Consulta propuesta para Logs Insights, limitada al grupo consumidor y a la
ventana de la prueba:

```text
fields @timestamp, @message
| filter @message like /Mensaje SQS .* procesado\. SES ID:|Error procesando mensaje SQS|Tiempo insuficiente/
| sort @timestamp asc
| limit 200
```

Para contar mensajes distintos que alcanzaron SES:

```text
fields @message
| parse @message "Mensaje SQS * procesado. SES ID: *" as sqs_id, ses_id
| filter ispresent(sqs_id)
| stats count(*) as registros_procesados, count_distinct(sqs_id) as mensajes_sqs_distintos
```

No exigir igualdad exacta de contadores AWS con 30: puede haber menos POST,
tráfico previo, duplicados o reintentos. Los IDs de SNS y SQS son diferentes.
La aplicación actual no tiene correlación completa entre cada POST y cada
mensaje consumidor; usar una ventana aislada y evidencia conjunta. Un éxito
de SES con el simulador no demuestra entrega a Gmail.

## Qué conservar en evidencias

Conservar el resumen JSON de k6, consola opcional, fecha/ventana/zona horaria,
versión de k6 y configuración (10 VUs, 60 s, máximo 3 POST/VU, timeout 30 s).
Guardar capturas/exportaciones de las métricas y las líneas de procesamiento
o fallo del consumidor. Anotar si hubo arranques en frío.
Registrar el total real de POST y sus 202, estado final de colas y mensajes SQS
distintos procesados, indicando las limitaciones de correlación.
No añadir resultados supuestos ni incluir el JWT o una exportación del entorno.

Referencias: [SNS](https://docs.aws.amazon.com/sns/latest/dg/sns-monitoring-using-cloudwatch.html),
[SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html),
[Lambda](https://docs.aws.amazon.com/lambda/latest/dg/monitoring-metrics-types.html)
y [fallos parciales SQS/Lambda](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-errorhandling.html).
