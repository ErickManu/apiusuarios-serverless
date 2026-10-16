# Actividad 4 — análisis de degradación observada

## Alcance real

Se utiliza la ráfaga controlada de 50 VUs de la Actividad 2 y su JSON original.
No se realizó una prueba de estrés incremental completa. Los escenarios de
100, 250 y 500 VUs están preparados pero bloqueados y carecen de mediciones.
No existe una curva medida que permita ubicar un punto de ruptura entre esos
niveles ni predecir sus porcentajes de errores.

## Hallazgos respaldados

En A2 hubo 50 solicitudes, 10 aceptadas y 40 fallidas (80 %), con P95 global
de 25687,66 ms y P99 de 26104,94 ms. La media global de 5127,91 ms es engañosa
si se interpreta como mejora: la media de las aceptadas fue 25295,21 ms.
Por diferencia de agregados, la media fallida es 86,09 ms; se etiqueta como
cálculo derivado y no como una nueva medición individual.

El usuario confirmó en CloudWatch 40 throttles y máximo de concurrencia 10,
con una cuota regional de 10. Esto respalda que el límite de concurrencia fue
el cuello de botella operativo de esta ejecución. No se está midiendo la
capacidad intrínseca ilimitada de Spring Boot, SNS o PostgreSQL.

Los arranques en frío lentos prolongan la ocupación de los entornos aceptados
y pueden agravar los rechazos. El backend mantiene arranque síncrono de Spring
y validación JPA al iniciar; no se cambió esa configuración para la entrega.
No se atribuyen los 40 fallos a JWT, SES o un código HTTP específico sin registros.

## Limitaciones

El ensayo envió un POST por VU al comienzo y luego esperó. No fue carga sostenida
durante dos minutos. La Actividad 1 tuvo tres POST por VU distribuidos: tampoco
es una comparación experimental que aísle únicamente el número de VUs.
No hay timestamps individuales para separar de forma rigurosa frío/caliente.
Los códigos exactos de las 40 fallidas siguen pendientes. API Gateway puede
convertir un rechazo de invocación Lambda en 500, pero no se asigna ese código
a estas solicitudes sin evidencia.

## Mejoras propuestas sin ampliar la cuota

Reducir la ráfaga y planificar una concurrencia efectiva por debajo del total
regional, dejando margen al consumidor y a otras funciones. Eso sería otro
ensayo futuro, con su alcance documentado; no una ejecución ya hecha de 100 VUs.
Evaluar inicialización asíncrona compatible con el adaptador de Spring, medir
el coste de JPA/Neon y diferir componentes no utilizados por notificaciones.
Mantener inicialmente la validación del esquema y el pool pequeño; no aumentar
conexiones indiscriminadamente. Conservar el simulador y los cupos por VU.

Añadir observabilidad por estado HTTP ya quedó preparado en el script de 50 VUs,
pero no se ejecutó una segunda prueba para obtener ese desglose. Si más adelante
se considera retry, requerir presupuesto acotado, backoff e idempotencia: no
reintentar automáticamente POST de notificaciones con resultado incierto.

Referencias: [métricas de concurrencia Lambda](https://docs.aws.amazon.com/lambda/latest/dg/monitoring-metrics-types.html)
y [errores Lambda/API Gateway](https://docs.aws.amazon.com/lambda/latest/dg/services-apigateway-errors.html).
