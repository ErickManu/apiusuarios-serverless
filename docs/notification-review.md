# Revisión local de notificaciones — 8 de octubre de 2026

Correcciones preparadas y comprobadas sin despliegue. Se conserva:
Ionic → API Gateway REST → Lambda Java → SNS → SQS → Lambda Python → SES.
No se ejecutaron apply, destroy, GitHub Actions, push ni envíos de correo.
No se modificaron secretos, contraseñas ni `.env`. Maven descargó únicamente
dependencias de pruebas tras autorizarse la escritura en su caché local.

## Qué demuestra la evidencia sobre SNS

- Se inspeccionaron todos los `.tf`: backend, provider, versiones, variables,
  main, backend-sns, notifications, notification-lambda y outputs.
- `3eca92e` añadió `aws_sns_topic.notifications`, con el nombre derivado de
  `project_name` y `stage_name`. `69b7750` añadió la DLQ sin cambiar el tema.
  `286f0a6` cambió la verificación del workflow, sin modificar SNS.
- No hay `count`, `for_each`, `removed`, `terraform destroy`, `DeleteTopic`
  ni provisioners que eliminen el tema en la configuración inspeccionada.
  Sus dependientes (suscripción, política SQS y backend) no indican que deba
  eliminarse al reemplazar el objeto JAR.
- El `terraform/tfplan` local contiene la creación inicial del backend y no
  contiene SNS. **No es el plan reportado de 3 creaciones, 3 modificaciones
  y 1 eliminación.** Se inspeccionó su JSON en memoria, mostrando únicamente
  direcciones/acciones, sin mostrar valores privados.
- Los dos respaldos locales de estado tienen serial 28 y no incluyen SNS.
  No describen el estado remoto actual. No se consultó ni alteró ese estado.

**La causa exacta de la eliminación no está demostrada.** Hay mecanismos
que deben contrastarse con el registro del despliegue, no asumirse:

1. Aplicar una revisión anterior del código, sin el recurso SNS, sobre el mismo
   estado remoto puede planificar su eliminación. El historial sí contiene un
   workflow anterior automático por `push` (`26e514c`), pero no disponemos de
   sus ejecuciones ni de sus planes para atribuirle este incidente.
2. Un reemplazo forzado/tainted o una instancia deposed con el mismo nombre
   merece revisión. `aws_api_gateway_deployment.backend` tiene
   `create_before_destroy`, y su cadena de dependencias alcanza Lambda y SNS.
   Terraform puede propagar esa regla a dependencias. SNS `CreateTopic` devuelve
   el ARN existente cuando ya existe un tema del mismo propietario y nombre;
   por eso un ciclo crear/retiro de una instancia anterior del mismo ARN puede
   ser peligroso. No hay evidencia local de taint, deposed o `-replace` en este
   incidente: este mecanismo es una hipótesis, no una conclusión.
3. Una eliminación externa o un cambio de cuenta/región requiere los eventos
   AWS correspondientes. El workflow ahora fija `us-east-2`, evitando depender
   de un secreto de región que pudiera estar vacío o diferir del backend.

El reemplazo de `aws_s3_object.lambda_code` es esperable: la clave incluye el
hash del JAR. Esa eliminación corresponde al objeto del artefacto anterior,
no al tema SNS.

Fuentes para los mecanismos: [lifecycle de Terraform](https://developer.hashicorp.com/terraform/language/meta-arguments/lifecycle)
y [CreateTopic de SNS](https://docs.aws.amazon.com/sns/latest/api/API_CreateTopic.html).

## Configuración revisada y conservada

- Tema estándar con el mismo nombre y `prevent_destroy = true` del usuario.
- Cola estándar con visibilidad de 180 segundos; Lambda Python de 30 segundos:
  cumple el margen de seis veces el timeout, sin ventana adicional de lote.
- DLQ con 14 días de retención y redrive de la cola principal tras
  `maxReceiveCount = 3`; la retención principal predeterminada es de cuatro días.
  Se conserva ese umbral. AWS recomienda al menos cinco intentos para tolerar
  errores transitorios; puede revisarse después, sin alterar ahora el umbral.
- Política SQS: solo `sns.amazonaws.com`, `sqs:SendMessage`, ARN de esta cola
  y condición `aws:SourceArn` de este tema. Suscripción con
  `raw_message_delivery = true`, creada después de la política SQS.
- Mapping SQS → Lambda con lote de cinco y `ReportBatchItemFailures`.
  No hace falta permiso `lambda:InvokeFunction` para SNS/SQS en este flujo:
  Lambda consume la cola mediante el rol de ejecución.
- Publicación del backend: solo `sns:Publish` sobre el tema. SES: solo
  `ses:SendEmail` sobre la identidad de correo configurada, en la misma región.
- Por autorización del usuario, se sustituyeron los dos attachments
  administrados de la Lambda Python por permisos de logs sobre su log group y
  `ReceiveMessage`, `DeleteMessage`, `GetQueueAttributes` sobre su cola.
  No se concedieron permisos administrativos ni se añadieron servicios.
- La Lambda Java ahora espera la política de publicación y la suscripción
  antes de actualizarse. IAM puede requerir propagación al desplegar; esto no
  prueba ni corrige por sí mismo una eliminación del tema.

Referencia: [configuración SQS/Lambda](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html).

## Correcciones del código

- `NotificationConfig.java`: cliente SNS inyectable y diferido, región explícita
  y timeout total de publicación de 15 segundos, menor que los 28 del backend.
  El arranque del backend local no requiere resolver credenciales AWS.
- `NotificationService.java`: conserva el JSON `email`, `subject`, `message`;
  comprueba el tamaño UTF-8 del JSON serializado contra los 256 KiB actuales
  del tema, incluyendo los escapes. No crea temas ni oculta errores de SNS.
- `NotificationRequest.java`: valida campos obligatorios, correo, longitudes
  y asunto sin saltos de línea.
- `NotificationController.java`: conserva `POST /notifications/send` y 202
  al publicar; devuelve 400 para campos inválidos, 413 para carga excesiva y
  503 si SNS/configuración no están disponibles. No devuelve detalles AWS.
  Los manejadores de error se limitan a este controlador.
- `lambda_function.py`: valida el JSON, mantiene UTF-8 y devuelve únicamente
  los identificadores fallidos. Limita el tiempo de SES, comprueba tiempo
  restante y devuelve también los registros pendientes antes de agotar el
  timeout. Registra identificador SQS, identificador SES y tipo/código de error
  en CloudWatch, sin registrar destinatarios ni contenido del mensaje.
- No se modificaron `SecurityConfig`, el filtro JWT ni los controladores
  anteriores. Pruebas con el filtro real y eventos API Gateway confirman el
  rechazo sin JWT/con JWT inválido, 202 con JWT válido y preflight de Ionic.

Las respuestas parciales evitan reintentar los registros exitosos del mismo
lote, pero SQS estándar puede entregar duplicados. Si SES acepta un correo y
la conexión se pierde antes de confirmar la respuesta, puede enviarse de nuevo.
No se añadió almacenamiento de deduplicación ni otro servicio.
[Referencia AWS](https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html).

## Workflow y protección del tema

`deploy.yml` conserva exclusivamente `workflow_dispatch`, rama `main`,
`desplegar: false`, concurrencia serial y apply condicionado al booleano.
Se añadieron pruebas offline y consultas precisas para una eventual verificación
posterior al despliegue: el estado Terraform por sí solo no demuestra existencia
de recursos. Esas consultas no se ejecutaron en esta revisión.

`scripts/check_terraform_plan.py` comprueba el JSON del plan antes de apply:
exige la configuración de notificaciones y bloquea eliminaciones/reemplazos
inesperados. Excepciones limitadas: reemplazar el JAR en el mismo bucket y
retirar los dos attachments IAM autorizados cuando la política inline está
presente sobre el mismo rol. Solo imprime direcciones/acciones, sin valores.

`prevent_destroy` bloquea planes que destruyan/reemplacen el tema mientras
la declaración siga presente. No protege frente a borrar la declaración,
eliminarlo directamente en AWS ni aplicar una revisión anterior sin esa regla.
Cambiar `project_name`/`stage_name` puede exigir reemplazar el tema y quedar
bloqueado. No quitar la protección para forzar un plan.

## Comprobaciones realizadas

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress --offline -Plambda package
python -m unittest discover -s notification-lambda -p 'test_*.py' -v
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python -m py_compile notification-lambda/lambda_function.py scripts/check_terraform_plan.py
terraform -chdir=terraform fmt -recursive
terraform -chdir=terraform fmt -check -recursive
terraform -chdir=terraform validate -no-color
terraform -chdir=terraform test -no-color
git diff --check
```

- Maven Java 21: **BUILD SUCCESS**, 23 pruebas sin fallos, JAR Lambda generado.
  Se comprobaron login, CRUD, datos, archivos, JWT/CORS y notificaciones.
  Se conservan avisos del empaquetado Shade sobre metadatos compartidos.
- Python: 7 pruebas del consumidor y 7 del verificador del plan, sin fallos.
- Terraform: formato y validación correctos; un plan de pruebas con siete
  aserciones, proveedores AWS/archive simulados, sin recursos reales.
  El archivo `pom.xml` sirve solo de fixture de hash en esa prueba.
- Las pruebas utilizan H2 y clientes AWS simulados: no acceden a Neon ni SES.
- No se generó un plan real actualizado; los resultados no confirman el estado
  actual de AWS ni la entrega real de correos.

## Archivos modificados o añadidos

| Archivo | Cambio |
| --- | --- |
| `src/main/java/com/erick/apiusuarios/application/service/NotificationService.java` | Inyección SNS y límite del JSON |
| `src/main/java/com/erick/apiusuarios/infrastructure/config/NotificationConfig.java` | Nuevo cliente SNS diferido |
| `src/main/java/com/erick/apiusuarios/infrastructure/controller/NotificationController.java` | Validación y errores HTTP locales |
| `src/main/java/com/erick/apiusuarios/infrastructure/controller/NotificationRequest.java` | Restricciones de entrada |
| `notification-lambda/lambda_function.py` | Validación, timeout y fallos parciales |
| `terraform/main.tf` | Dependencias del backend |
| `terraform/notification-lambda.tf` | Permisos IAM limitados autorizados |
| `terraform/outputs.tf` | Identificadores para verificación futura |
| `terraform/notifications.tf` | Protección SNS preexistente del usuario, conservada |
| `.github/workflows/deploy.yml` | Pruebas, región y comprobación del plan |
| `.gitignore` | Ignorar ZIP, planes legibles y cachés de Python |
| `scripts/check_terraform_plan.py` | Nuevo bloqueo de eliminaciones inesperadas |
| `scripts/tests/test_check_terraform_plan.py` | Pruebas del bloqueo |
| `notification-lambda/test_lambda_function.py` | Pruebas sin boto3 real |
| `src/test/java/com/erick/apiusuarios/application/service/NotificationServiceTest.java` | JSON, límites y errores SNS |
| `src/test/java/com/erick/apiusuarios/ApiusuariosApplicationTests.java` | JWT, validación y HTTP de notificaciones |
| `src/test/java/com/erick/apiusuarios/infrastructure/lambda/LambdaIntegrationTest.java` | Notificaciones por API Gateway simulado |
| `src/test/resources/application-test.properties` | ARN ficticio para pruebas |
| `terraform/tests/notifications.tftest.hcl` | Contratos Terraform con mocks |
| `terraform/README.md` | Documentación del flujo y comprobaciones |
| `docs/notification-infrastructure-proposal.md` | Propuesta IAM autorizada |
| `docs/notification-review.md` | Evidencia, resultados y pendientes |

## Pendiente antes de cualquier despliegue

1. Revisar el log exacto del despliegue que creó/eliminó el tema y sus acciones
   Terraform. Consultar los eventos existentes `CreateTopic`/`DeleteTopic` de
   CloudTrail en `us-east-2` para identificar hora, identidad y origen. No hace
   falta crear trails para esta investigación si esos eventos ya están disponibles.
2. Confirmar cuenta, región, workspace `default` y estado S3 del backend.
   No migrar, borrar ni restaurar estado basándose en los respaldos anteriores
   a SNS. Generar un plan nuevo y revisarlo; no reutilizar `terraform/tfplan`.
   El plan puede incluir ahora una política IAM nueva y dos retiradas de
   attachments autorizadas, además de los cambios reportados anteriormente.
3. Verificar en SES `us-east-2` la identidad exacta de correo del remitente.
   La política actual apunta al ARN de esa dirección; verificar solo un dominio
   puede exigir revisar el ARN autorizado. En sandbox también deben estar
   verificados los destinatarios, salvo el simulador SES. Revisar cuotas y
   situación de la cuenta antes de enviar pruebas reales.
   [Referencia SES](https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html).
4. Revisar que `UsuariosMobile` utilice el API base URL con `/dev`, el path
   `/notifications/send`, JSON y `Authorization: Bearer <JWT>`.
   El frontend separado no se encontró dentro de este repositorio.
5. Tras autorización independiente de despliegue, verificar el flujo real y
   observar CloudWatch/DLQ. Un HTTP 202 significa publicación aceptada por SNS,
   no entrega del correo. La revisión actual no crea consumo de AWS ni añade
   componentes de pago; la elegibilidad y cuotas del plan gratuito de la cuenta
   no se comprobaron remotamente.
