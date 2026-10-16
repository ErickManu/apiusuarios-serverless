# Revisión previa de publicación — 16 de octubre de 2026

## Repositorio existente

- Remoto `origin` (fetch y push): `https://github.com/ErickManu/apiusuarios-serverless.git`.
- Rama local `main`, con seguimiento de `origin/main`.
- API oficial de GitHub consultada: repositorio **público**, rama principal `main`, no archivado.
- La consulta fue de solo lectura y sin autenticación; no se verificaron permisos de escritura.
- No se creó otro repositorio. Se autorizó preparar el staging de la práctica; commit y push requieren la aprobación posterior del autor. No se ejecutó k6 ni despliegue.

## Archivos propuestos

Este inventario identifica los cambios respecto a HEAD para preparar el índice.
El código ya versionado se conserva; solo hay un archivo rastreado modificado.
Los archivos nuevos incluyen material preparado en sesiones anteriores y el PDF
aportado por el autor; no significa que todos se hayan creado en esta revisión.

**41 incorporaciones y 1 archivo rastreado modificado.** No hay eliminaciones.
El staging se limita a estos 42 archivos; commit y push quedan pendientes de aprobación.

| Estado | Archivo |
| --- | --- |
| Modificado | [.gitignore](../.gitignore) |
| Nuevo | [README.md](../README.md) |
| Nuevo | [docs/ENTREGA-GITHUB.md](../docs/ENTREGA-GITHUB.md) |
| Nuevo | [docs/REVISION-PUBLICACION.md](../docs/REVISION-PUBLICACION.md) |
| Nuevo | [pruebas-carga/ACTIVIDAD2-ESCENARIO1.md](../pruebas-carga/ACTIVIDAD2-ESCENARIO1.md) |
| Nuevo | [pruebas-carga/DIAGNOSTICO-ACTIVIDAD2.md](../pruebas-carga/DIAGNOSTICO-ACTIVIDAD2.md) |
| Nuevo | [pruebas-carga/README.md](../pruebas-carga/README.md) |
| Nuevo | [pruebas-carga/REVISION-ASINCRONA.md](../pruebas-carga/REVISION-ASINCRONA.md) |
| Nuevo | [pruebas-carga/actividad1-linea-base.js](../pruebas-carga/actividad1-linea-base.js) |
| Nuevo | [pruebas-carga/actividad2-escenario1-50vus.js](../pruebas-carga/actividad2-escenario1-50vus.js) |
| Nuevo | [pruebas-carga/evidencias/.gitkeep](../pruebas-carga/evidencias/.gitkeep) |
| Nuevo | [pruebas-carga/evidencias/README.md](../pruebas-carga/evidencias/README.md) |
| Nuevo | [pruebas-carga/evidencias/actividad1-20261016-005817.json](../pruebas-carga/evidencias/actividad1-20261016-005817.json) |
| Nuevo | [pruebas-carga/evidencias/actividad2-50vus-20261016-014407.json](../pruebas-carga/evidencias/actividad2-50vus-20261016-014407.json) |
| Nuevo | [pruebas-carga/evidencias/cloudwatch/README.md](../pruebas-carga/evidencias/cloudwatch/README.md) |
| Nuevo | [pruebas-carga/evidencias/derivadas/comparativa.json](../pruebas-carga/evidencias/derivadas/comparativa.json) |
| Nuevo | [pruebas-carga/evidencias/derivadas/graficos/01-solicitudes-y-errores.png](../pruebas-carga/evidencias/derivadas/graficos/01-solicitudes-y-errores.png) |
| Nuevo | [pruebas-carga/evidencias/derivadas/graficos/01-solicitudes-y-errores.svg](../pruebas-carga/evidencias/derivadas/graficos/01-solicitudes-y-errores.svg) |
| Nuevo | [pruebas-carga/evidencias/derivadas/graficos/02-latencias.png](../pruebas-carga/evidencias/derivadas/graficos/02-latencias.png) |
| Nuevo | [pruebas-carga/evidencias/derivadas/graficos/02-latencias.svg](../pruebas-carga/evidencias/derivadas/graficos/02-latencias.svg) |
| Nuevo | [pruebas-carga/evidencias/derivadas/graficos/03-throughput-controlado.png](../pruebas-carga/evidencias/derivadas/graficos/03-throughput-controlado.png) |
| Nuevo | [pruebas-carga/evidencias/derivadas/graficos/03-throughput-controlado.svg](../pruebas-carga/evidencias/derivadas/graficos/03-throughput-controlado.svg) |
| Nuevo | [pruebas-carga/evidencias/originales/README.md](../pruebas-carga/evidencias/originales/README.md) |
| Nuevo | [pruebas-carga/evidencias/originales/manifest-sha256.json](../pruebas-carga/evidencias/originales/manifest-sha256.json) |
| Nuevo | [pruebas-carga/evidencias/plantillas/RECUPERACION-PENDIENTE.md](../pruebas-carga/evidencias/plantillas/RECUPERACION-PENDIENTE.md) |
| Nuevo | [pruebas-carga/evidencias/simulador-ses-2.json](../pruebas-carga/evidencias/simulador-ses-2.json) |
| Nuevo | [pruebas-carga/evidencias/simulador-ses.json](../pruebas-carga/evidencias/simulador-ses.json) |
| Nuevo | [pruebas-carga/evidencias/verificacion-2.json](../pruebas-carga/evidencias/verificacion-2.json) |
| Nuevo | [pruebas-carga/evidencias/verificacion-resumen.json](../pruebas-carga/evidencias/verificacion-resumen.json) |
| Nuevo | [pruebas-carga/herramientas/generar-informe.py](../pruebas-carga/herramientas/generar-informe.py) |
| Nuevo | [pruebas-carga/herramientas/requirements-informe.txt](../pruebas-carga/herramientas/requirements-informe.txt) |
| Nuevo | [pruebas-carga/informe/ACTIVIDAD4-DEGRADACION.md](../pruebas-carga/informe/ACTIVIDAD4-DEGRADACION.md) |
| Nuevo | [pruebas-carga/informe/ACTIVIDAD5-RECUPERACION.md](../pruebas-carga/informe/ACTIVIDAD5-RECUPERACION.md) |
| Nuevo | [pruebas-carga/informe/INFORME-TECNICO.md](../pruebas-carga/informe/INFORME-TECNICO.md) |
| Nuevo | [pruebas-carga/informe/Informe De Pruebas Paralelo 1-21-2841.pdf](../pruebas-carga/informe/Informe%20De%20Pruebas%20Paralelo%201-21-2841.pdf) |
| Nuevo | [pruebas-carga/informe/RESUMEN-VISUAL.html](../pruebas-carga/informe/RESUMEN-VISUAL.html) |
| Nuevo | [pruebas-carga/pendientes/README.md](../pruebas-carga/pendientes/README.md) |
| Nuevo | [pruebas-carga/pendientes/actividad2-100vus.js](../pruebas-carga/pendientes/actividad2-100vus.js) |
| Nuevo | [pruebas-carga/pendientes/actividad2-250vus.js](../pruebas-carga/pendientes/actividad2-250vus.js) |
| Nuevo | [pruebas-carga/pendientes/actividad2-500vus.js](../pruebas-carga/pendientes/actividad2-500vus.js) |
| Nuevo | [pruebas-carga/pendientes/escenario-bloqueado.js](../pruebas-carga/pendientes/escenario-bloqueado.js) |
| Nuevo | [scripts/verificar-entrega.ps1](../scripts/verificar-entrega.ps1) |

Los seis JSON originales, los dos scripts activos y los escenarios bloqueados
se conservan. Las gráficas PNG/SVG, el resumen HTML, el PDF y los informes sí
forman parte de la entrega; no son carpetas de dependencias. Ningún candidato
revisado supera 5 MiB. El PDF ocupa 649333 bytes (aproximadamente 634 KiB).

## Cambios de esta revisión

- README principal: práctica, PDF definitivo, requisitos, comandos PowerShell, resultados y pendientes.
- Guías de entrega, actividad 2 e índices: corregidas referencias antiguas a escenarios y capturas pendientes.
- `.gitignore`: conserva exclusiones de secretos/estados y añade dependencias, cachés y volcados locales.
- Verificador local: añade patrones para tokens GitHub y URLs con usuario y contraseña.
- Este inventario: archivos propuestos y observaciones antes de publicar.

No se modificaron backend, aplicación móvil, Terraform, workflows, scripts k6,
JSON originales, gráficos ni contenido del PDF en esta revisión.

## Revisión de secretos y privacidad

Se revisaron los candidatos nuevos y los archivos rastreados en el árbol actual.
No se detectaron credenciales reales, JWT ni claves privadas en las comprobaciones
de patrones; una revisión complementaria encontró dos literales de prueba en
un test Terraform existente, identificados como datos ficticios de prueba.
No se cambiaron esos tests. No hay archivos rastreados que coincidan con las
exclusiones de `.gitignore`.

En la revisión adicional del historial local se examinaron 12 commits y 107
versiones únicas de archivos, incluyendo referencias y reflogs. No se detectaron
JWT, claves de acceso AWS, claves privadas, tokens de proveedor, URLs con
credenciales ni contraseñas reales codificadas. Tampoco se encontraron archivos
`.env`, estados/variables reales Terraform o archivos de claves versionados en
esos commits. Se descartaron coincidencias con referencias a variables y datos
ficticios de tests; también se revisaron los mensajes de commit sin detectar
señales de secretos. El repositorio local no es una copia superficial.
El alcance es el historial disponible localmente: no se inspeccionaron ramas
remotas no descargadas, objetos eliminados ni otros repositorios. No se reescribió
el historial ni se descargaron cambios mediante `git fetch`.

El verificador no inspecciona binarios. El PDF se revisó por separado: extracción
de texto y revisión visual de ocho páginas. No se detectaron tokens o claves.
**Una captura muestra identificadores de cuenta y recursos AWS (ARN y nombres),**
y la portada incluye datos académicos del autor. Los identificadores no son
credenciales, pero su exposición debe revisarse porque el repositorio es público.
El PDF no se editó. El autor confirmó expresamente conservarlo exactamente como
está, con capturas e identificadores AWS, y conoce que el repositorio es público.
No queda pendiente una decisión de anonimización para esta entrega.
En la página 8 aparecen:

| Dato visible | Detalle para decidir su publicación |
| --- | --- |
| ID de cuenta AWS | Número completo en la captura; aquí se indica solo su terminación `9719` |
| Tema SNS | Nombre `apiusuarios-dev-notifications` y ARN con región e ID de cuenta |
| Destino SQS | ARN parcialmente visible en la columna de punto de enlace |
| Suscripción SNS | UUID completo visible; comienza por `41c04172` |
| Región | `us-east-2` |
| Registros del consumidor | RequestId de Lambda, IDs de mensajes SQS y de envíos SES, timestamps |
| Runtime Lambda | ARN de versión del runtime administrado Python |

No se observa un JWT ni una clave en esas capturas. La línea que informa que se
encontraron credenciales en variables de entorno no imprime sus valores. Se
puede ocultar la cuenta, ARN, suscripción e IDs de correlación en una futura copia
si se desea evitar divulgar detalles operativos; el autor eligió conservar esta
versión. No son credenciales que permitan acceder por sí solas. La portada
también incluye los datos académicos previstos por el autor.

Huella SHA-256 del PDF, verificada sin alterarlo:

```text
E8FF2895F81FE005FC3D3C228594B55DF1DA5DB68E3A17FE15D6284065875366
```

Los archivos `.env`, estados/planes Terraform, variables reales, claves,
credenciales AWS, dependencias y logs privados quedan excluidos.
No forzar su inclusión. Las reglas son una ayuda, no garantizan detectar todos
los secretos. Las futuras capturas o exportaciones necesitan su propia revisión.

## Evidencia presente y límites de lo medido

- Actividad 1: JSON original, 30 solicitudes aceptadas, 0 % de errores.
- Actividad 2: JSON original, 10 aceptadas y 40 fallidas de 50; 80 % de errores.
- Seis resúmenes JSON originales en total, incluyendo verificaciones previas.
- Comparativa derivada, tres gráficos en PNG/SVG, informe Markdown y HTML local.
- PDF definitivo con capturas de consola, CloudWatch, cuota regional y SNS.
- Escenarios 100/250/500: preparados, bloqueados y NO EJECUTADOS; no hay mediciones.
- No se realizó un estrés incremental completo. Tiempo real de recuperación pendiente.

Faltan exportaciones históricas independientes con timestamps y ventanas
comparables para calcular recuperación y, si existen registros que los conserven,
los códigos HTTP exactos de las 40 respuestas fallidas. No se deducen esos códigos
a partir de los throttles. Las capturas del PDF no sustituyen series temporales.

## Comprobaciones locales

Los enlaces relativos del README y de las guías actualizadas apuntan a archivos
existentes y candidatos no ignorados, incluido el PDF con espacios codificados.
Se comprobaron 78 enlaces locales sin referencias rotas. El verificador revisó
111 candidatos (rastreados y nuevos) sin señales detectadas por sus reglas; la
sintaxis de siete bloques PowerShell y del verificador no presentó errores.
En la revisión adicional se comprobaron de nuevo los 19 enlaces locales del
README sin referencias rotas y se confirmó por la API de GitHub la visibilidad
pública del repositorio. El PDF todavía no está versionado: su enlace relativo
quedará disponible en GitHub cuando se publique junto con el README.
El HTML se abre localmente; en GitHub se conserva como archivo descargable.

Para repetir solo la revisión local, sin tráfico a AWS:

```powershell
.\scripts\verificar-entrega.ps1
git status --short
git diff --stat
git diff --cached --name-only
```

El envío a GitHub queda pendiente de la aprobación del autor sobre el staging.
Después de esa aprobación se usará el mensaje de commit solicitado:
`Entrega final - Pruebas de carga y rendimiento - Tarea 5`, y `git push origin main`.
No se prepararon automatismos de despliegue ni de pruebas de carga.
