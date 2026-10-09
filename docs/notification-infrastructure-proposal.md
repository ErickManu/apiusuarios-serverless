# Migración IAM de notificaciones en dos fases

La fase 1 está preparada únicamente en archivos locales, por autorización del
usuario. La fase 2 está documentada y pendiente de autorización independiente.
No se aplicaron cambios ni se consultó el estado remoto durante esta preparación.

## Fase 1: conservar los permisos actuales y añadir los limitados

Se mantienen las mismas direcciones, rol y ARN de los attachments existentes:

| Recurso Terraform | Política administrada conservada |
| --- | --- |
| `aws_iam_role_policy_attachment.notification_basic` | `AWSLambdaBasicExecutionRole` |
| `aws_iam_role_policy_attachment.notification_sqs` | `AWSLambdaSQSQueueExecutionRole` |

También se conserva `aws_iam_role_policy.notification_runtime`, con estos permisos:

| Acciones | Recurso autorizado |
| --- | --- |
| `logs:CreateLogStream`, `logs:PutLogEvents` | Streams del log group de la Lambda Python |
| `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes` | ARN de la cola de notificaciones |

SES mantiene `ses:SendEmail` sobre la identidad del remitente en `us-east-2`.
El log group sigue administrado por Terraform: la política inline no necesita
`logs:CreateLogGroup`. No se añaden permisos KMS ni administrativos.

La Lambda Python depende explícitamente de ambos attachments, de la política
inline nueva, de SES y del log group antes de actualizarse. El mapping depende
de la Lambda. Mantener los permisos anteriores evita un intervalo de revocación
mientras se crea y propaga la política nueva. `depends_on` ordena las operaciones,
pero no garantiza por sí solo la propagación inmediata de IAM.
[Referencia AWS sobre propagación](https://docs.aws.amazon.com/IAM/latest/UserGuide/troubleshoot.html#troubleshoot_general_eventual-consistency).

Se conservan las dependencias del backend Java respecto de su política SNS y
la suscripción, sus rutas, JWT, PostgreSQL y S3. El tema mantiene su nombre y
`prevent_destroy = true`. El workflow sigue siendo exclusivamente manual,
con `desplegar: false` por defecto.

El verificador del plan exige ambos attachments en la configuración y rechaza
cualquier eliminación o reemplazo de ellos, incluso si existe la política inline.
La única eliminación permitida sigue siendo el reemplazo del objeto JAR dentro
del mismo bucket. No existe un interruptor que active automáticamente la fase 2.
Se revisan todas las entradas del plan, incluidas instancias `deposed` que
compartan dirección con la instancia actual: una creación no oculta una retirada.

Antes de una aplicación autorizada, generar y revisar un **plan nuevo** sobre
la misma cuenta, región `us-east-2`, workspace `default` y backend S3. Los dos
attachments deben aparecer sin cambios si el estado sigue como en el último
plan de GitHub (#12). Si faltaran realmente, podrían proponerse creaciones,
pero nunca retiradas. No reutilizar el plan previo de 4/4/3 ni el `tfplan` local.
La restauración de estas declaraciones elimina las dos retiradas previstas;
el número total de acciones debe comprobarse en el nuevo plan remoto.

Si SNS sigue ausente, se espera `create` con su nombre actual y después la
suscripción. No debe aparecer `delete`, reemplazo ni destrucción de una instancia
deposed del tema. Si la protección bloquea el plan, detenerse y revisar el estado;
no quitarla, no hacer `untaint`, `state rm` ni restaurar estados antiguos para
forzar la aplicación. La prueba local con mocks verifica su creación desde un
estado vacío, pero no reproduce el estado remoto.

## Fase 2: retirada futura, todavía no ejecutada

1. Después de aplicar la fase 1 con autorización, confirmar en el mismo rol que
   existen ambas políticas administradas, la inline `notification_runtime` y
   SES; comparar sus ARN con la cola, el log group y el remitente reales.
   Revisar posibles denegaciones por boundaries, SCP o políticas de recursos.
2. Verificar publicación con JWT, consumo de SQS, registros CloudWatch,
   entrega SES y fallos parciales/DLQ mediante pruebas autorizadas y acotadas.
   Revisar errores `AccessDenied`, backlog y estado del mapping. SES debe tener
   la identidad/región correctas y respetar las restricciones del sandbox.
3. **Los éxitos mientras coexisten políticas amplias no demuestran que la inline
   sea suficiente por sí sola.** Simular únicamente su JSON en modo Custom
   (o excluir las dos administradas de la simulación), sin desadjuntar nada.
   Evaluar las cinco acciones logs/SQS contra sus ARN reales y comprobar que
   recursos ajenos se deniegan; evaluar SES con su política separada. Incluir
   los controles restrictivos aplicables. Una simulación tampoco sustituye
   la comprobación real posterior; tiene limitaciones.
   [Simulador IAM de AWS](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_testing-policies.html).
4. Solo con estas evidencias y autorización nueva, preparar un cambio separado:
   retirar exclusivamente los dos bloques `aws_iam_role_policy_attachment` y
   sus referencias en `depends_on`, conservando la inline, SES y el log group.
   Actualizar entonces el verificador y sus pruebas: permitir únicamente estas
   dos retiradas sobre el mismo rol y con la inline ya existente sin cambios;
   seguir bloqueando eliminaciones de roles, Lambdas, SNS, colas y log groups.
5. Revisar un plan nuevo. Para aislar la migración, no actualizar código,
   configuración del backend ni otros recursos en la misma operación. Si el
   plan contiene más cambios, detenerse y separarlos. Realizar la retirada
   en una ventana controlada después de autorización, sin prometer interrupción
   cero por las limitaciones del simulador y la propagación de IAM.
6. Verificar de nuevo logs, consumo SQS, SES y ausencia de `AccessDenied`.
   Ante fallos, restaurar localmente ambos bloques con sus direcciones originales
   y ambas dependencias, regenerar un plan y autorizar la recuperación de los
   attachments. Su reaplicación también requiere propagación; no amplificar la
   inline a `Resource = "*"` ni conceder administración para solucionar el fallo.

Hasta completar esta fase, permanecen los permisos amplios de las políticas
administradas actuales. Es una superposición temporal deliberada, sin ampliación
de los permisos que ya tenía el rol.
