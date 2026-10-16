# Catálogo de JSON originales, conservados en la carpeta superior

| Archivo | Clasificación | Uso en el informe |
| --- | --- | --- |
| [actividad1-20261016-005817.json](../actividad1-20261016-005817.json) | Actividad 1, 30 solicitudes | Comparación principal |
| [actividad2-50vus-20261016-014407.json](../actividad2-50vus-20261016-014407.json) | Actividad 2, 50 solicitudes | Comparación y degradación |
| [verificacion-resumen.json](../verificacion-resumen.json) | Verificación individual, fallida | Antecedente; no mezclar con actividades |
| [verificacion-2.json](../verificacion-2.json) | Verificación individual, exitosa | Antecedente; no clasificar frío/caliente solo por latencia |
| [simulador-ses.json](../simulador-ses.json) | Verificación individual, fallida | Antecedente; código HTTP no reconstruible |
| [simulador-ses-2.json](../simulador-ses-2.json) | Verificación individual, exitosa | Antecedente; no mezclar con actividades |

`manifest-sha256.json` contiene ruta relativa, tamaño y SHA-256. Los hashes prueban
integridad respecto de la copia local, no certifican autoría ni fechas de AWS.
Las fechas de los nombres corresponden al equipo que ejecutó k6; no sustituyen
los timestamps de la consola o de CloudWatch. El `.gitkeep` previo se conserva.
