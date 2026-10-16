# Registro de recuperación — pendiente, sin medición calculada

| Campo | Estado |
| --- | --- |
| Ventana y zona horaria originales | Pendiente |
| T0: última llegada HTTP de la ráfaga | Pendiente |
| Nivel base de la cola antes de la ráfaga | Pendiente |
| Serie SQS con timestamps y estadística | Pendiente |
| Serie Lambda backend/consumidor con timestamps | Pendiente |
| Nuevos mensajes en DLQ o fallos parciales | Pendiente de evidencia |
| Primer intervalo candidato estable | Pendiente |
| Confirmación de estabilidad durante 3 muestras consecutivas | Pendiente |
| Tiempo real de recuperación de la cola | No calculable con los agregados actuales |
| Recuperación HTTP confirmada con tráfico histórico posterior | Pendiente |
| Precisión temporal e incertidumbre | Pendiente, según periodo de exportación |

No completar con duración configurada de k6, latencia HTTP, timeout o tiempo de
visibilidad SQS: ninguno equivale al tiempo real de recuperación observado.
