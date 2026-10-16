# Evidencia CloudWatch: capturas en PDF y exportaciones pendientes

Confirmado por el usuario: región us-east-2, cuota regional Lambda de 10,
40 throttles en la prueba de 50 VUs y concurrencia máxima observada de 10.
También confirmó revisión de SNS, SQS, consumidor y SES. El
[PDF definitivo](../../informe/Informe%20De%20Pruebas%20Paralelo%201-21-2841.pdf)
contiene capturas de throttles/concurrencia, cuota, SNS y registros de consumo.
Estas capturas apoyan lo observado, pero no sustituyen una serie histórica
exportada ni permiten calcular el tiempo real de recuperación. En esta carpeta
todavía no hay exportaciones independientes; no se fabricaron datos para llenarla.

Preparar evidencias históricas, sin repetir la carga ni cambiar recursos:

| Archivo independiente sugerido | Contenido que debe conservar |
| --- | --- |
| `cuota-lambda.png` | Cuota regional efectiva, región y fecha de consulta |
| `lambda-throttles-concurrencia.png` | Throttles Sum y ConcurrentExecutions Maximum en ventana de A2 |
| `lambda-historico.csv` | Timestamp, recurso, métrica, estadística, unidad y valor real |
| `sqs-historico.csv` | Visible, NotVisible, AgeOfOldest, Received y Deleted, incluida DLQ |
| `sns-historico.csv` | Published, Delivered y Failed en la misma ventana |
| `consumidor-ses-sanitizado.txt` | Hora, ID SQS, ID SES y clase de fallo si la hubo |
| `api-gateway-estados-sanitizado.json` | Conteos por código si existían logs de acceso/ejecución |

No crear estos archivos con ceros o ejemplos para rellenar la entrega. Exportar
solo lo que exista; si un dato falta, registrarlo como pendiente. Se recomienda
periodo de un minuto para comparar las series, conservando estadística/unidad.

Omitir JWT, credenciales, IP, URL privada, correos reales y cuerpos/cabeceras.
Los borradores sin revisar pueden conservarse localmente en `privados/`, que
está ignorado por Git. La guía de recuperación está en
[ACTIVIDAD5-RECUPERACION.md](../../informe/ACTIVIDAD5-RECUPERACION.md).
