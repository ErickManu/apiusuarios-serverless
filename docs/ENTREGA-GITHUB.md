# Estructura de la entrega en GitHub

El repositorio local está preparado con README, backend, Terraform declarativo,
scripts k6, escenarios pendientes, informe, gráficos y evidencias agregadas.
El remoto `origin`, tanto fetch como push, apunta al repositorio existente
[ErickManu/apiusuarios-serverless](https://github.com/ErickManu/apiusuarios-serverless).
La rama local `main` tiene configurado seguimiento de `origin/main`. Se comprobó
la configuración local con `git remote -v` y Git. La API oficial de GitHub confirmó
que el repositorio es **público**, no está archivado y su rama principal es `main`.
No se comprobaron permisos de escritura. No hace falta crear otro repositorio.

El autor autorizó preparar el staging de la práctica; commit y push quedan
pendientes de su aprobación expresa de la lista preparada. El
[inventario de revisión](REVISION-PUBLICACION.md) identifica los archivos nuevos
y el cambio de `.gitignore` que se proponen para la entrega.

## Qué conservar y qué excluir

Publicar el código fuente, los scripts y documentación, el informe, los gráficos,
los JSON agregados k6 revisados y las capturas/exportaciones CloudWatch sanitizadas.
El [PDF definitivo](../pruebas-carga/informe/Informe%20De%20Pruebas%20Paralelo%201-21-2841.pdf)
ya contiene capturas; no se modificó su contenido. Una captura muestra
identificadores de cuenta y recursos AWS: no son claves de acceso. El autor
confirmó expresamente conservar el PDF íntegro, incluidas las capturas y estos
identificadores, sabiendo que el repositorio es público. También contiene los
datos académicos del autor. No se modificó el PDF.
Los originales JSON permanecen en sus rutas para no romper referencias; el índice
y el manifiesto indican su procedencia y sus hashes SHA-256.

`.gitignore` excluye `.env` y variantes privadas, credenciales AWS, claves privadas,
estados/planes Terraform, variables Terraform reales, directorios `.terraform`,
logs sin revisar y `evidencias/cloudwatch/privados/`. Los ejemplos declarativos y
`.terraform.lock.hcl` pueden conservarse: no equivalen a un estado Terraform.
No guardar JWT en archivos ni comandos. No subir exportaciones completas del
entorno o cuerpos/cabeceras de solicitudes.

La exclusión no elimina archivos locales y no retira archivos que ya estuvieran
versionados. Por eso se revisan tanto archivos rastreados como nuevos candidatos.

## Revisión local, sin despliegue

Desde la raíz:

```powershell
.\scripts\verificar-entrega.ps1
git status --short
git diff --stat
git ls-files
```

El verificador identifica nombres privados y señales comunes de claves/JWT en
los candidatos no ignorados. Solo informa ubicación y regla; nunca muestra el
valor detectado. Es una ayuda, no una garantía de detectar todos los secretos.
Omite binarios: el PDF se revisó aparte mediante extracción de texto e inspección
visual de sus ocho páginas. El verificador automático no audita el historial.
Además se revisaron por separado los 12 commits y las 107 versiones de archivos
del historial local disponible, incluyendo referencias y reflogs, sin detectar
secretos reales ni archivos privados versionados. Las coincidencias literales
correspondieron a datos ficticios de pruebas. Esta revisión no abarca ramas
remotas que no estén descargadas, objetos eliminados ni otros repositorios.
Revisar manualmente el contenido que se vaya a publicar, especialmente futuras
exportaciones CloudWatch. No usar `git add -f` para saltar las exclusiones.

No se añadió un workflow de carga ni de despliegue. El workflow de infraestructura
existente es manual y se conserva. No activarlo como parte de esta práctica.
La preparación de GitHub no requiere ejecutar k6 ni Terraform.

## Información pendiente para cerrar la entrega

El PDF y la URL del repositorio ya están identificados. Para ampliar la evidencia
técnica faltan exportaciones históricas con timestamps, ventana, zona horaria,
estadística y unidad de SNS/SQS/Lambda, y el desglose HTTP original de las 40
fallidas si aún existen logs que lo conserven. El tiempo real de recuperación
continúa pendiente; las capturas no permiten calcularlo. Los escenarios
100/250/500 siguen sin ejecutar y bloqueados. No se realizó una prueba de estrés
incremental completa. Ninguno de esos pendientes debe rellenarse con estimaciones
presentadas como mediciones.
