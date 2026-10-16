# Actividad 5 — recuperación a partir de métricas históricas

**Estado: guía preparada; tiempo real de recuperación pendiente.** Los resúmenes
k6 no contienen una serie temporal de SQS/Lambda. No se ejecutará otra carga
para completar datos ni se asignará una duración supuesta.

## Separar dos conceptos

La recuperación de la cola es volver al nivel base de pendientes y terminar
los mensajes admitidos. La recuperación del servicio HTTP exige evidencia de
solicitudes posteriores atendidas correctamente. Una cola vacía o throttles=0
sin tráfico no prueban que el servicio pueda aceptar de nuevo la misma ráfaga.
Los 40 rechazos Lambda no se convierten automáticamente en 40 mensajes SQS.

## Datos históricos necesarios

Obtener la ventana previa, la ráfaga y minutos posteriores en la misma región.
Conservar timestamps originales, zona horaria, recurso, unidad, estadística y
periodo (por ejemplo, un minuto). No activar logs ni métricas retroactivamente:
usar lo ya existente. La consulta/exportación puede hacerse después sin repetir
la prueba; esta preparación no consultó la cuenta AWS.

Revisar SQS: `ApproximateNumberOfMessagesVisible`,
`ApproximateNumberOfMessagesNotVisible`, `ApproximateAgeOfOldestMessage`,
`NumberOfMessagesReceived`, `NumberOfMessagesDeleted` y la DLQ. Para pendientes
y edad usar Maximum/último valor; para operaciones, Sum. Las aproximaciones y
las operaciones repetidas no constituyen conteos exactos de mensajes únicos.

Revisar backend y consumidor: `ConcurrentExecutions` Maximum, `Throttles`,
`Invocations`, `Errors` Sum, `Duration` Average/Maximum, INIT_REPORT y registros
de procesamiento. El consumidor captura fallos parciales: `Errors=0` no basta.
Corroborar los IDs SQS distintos procesados y la aceptación de SES en sus logs,
sin correos reales ni cuerpos. SNS Published/Delivered/Failed ayuda a establecer
qué mensajes entraron al flujo. No usar `AsyncEventAge` como si fuera edad de
mensajes SQS: corresponde al mecanismo de invocación asíncrona de Lambda.

## Procedimiento y criterio propuesto, no resultado observado

1. Identificar T0 como la última llegada HTTP de la ráfaga, usando registros
   históricos. No usar el final de los dos minutos: hubo espera sin nuevos POST.
2. Establecer el nivel base previo de Visible, NotVisible y edad de mensajes.
3. Buscar después de T0 la vuelta al nivel base, ausencia de nuevos fallos/DLQ
   y procesamiento satisfactorio del consumidor. Distinguir tráfico ajeno.
4. Como criterio de estabilidad propuesto, exigir tres muestras consecutivas
   sin degradación. No confundir ausencia de una muestra con un valor cero.
5. Definir `T_estable` como la hora de la tercera muestra que confirma estabilidad
   y calcular `T_recuperacion_confirmada = T_estable - T0` solo si existen ambos
   timestamps y las series necesarias. Reportar además el primer intervalo
   candidato y el periodo de muestreo para no sugerir precisión de milisegundos.
6. Analizar aparte si existe tráfico HTTP histórico posterior que confirme
   recuperación del servicio; sin ese tráfico, dejar esa conclusión pendiente.

La visibilidad SQS configurada en 180 s y los reintentos pueden retrasar fallos
o el traslado a DLQ. Es un parámetro de configuración, no una recuperación medida.
La edad del más viejo puede cambiar por reintentos o traslado de mensajes: no
deducir éxito solo porque esa edad disminuya.

## Entregable pendiente

Completar [RECUPERACION-PENDIENTE.md](../evidencias/plantillas/RECUPERACION-PENDIENTE.md)
con fuentes y marcas temporales reales. Adjuntar las exportaciones revisadas
en `evidencias/cloudwatch/`. Hasta entonces no se dibuja una curva de drenaje,
no se informa un tiempo real y no se afirma recuperación completa.

Referencias: [métricas SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html)
y [métricas Lambda](https://docs.aws.amazon.com/lambda/latest/dg/monitoring-metrics-types.html).
