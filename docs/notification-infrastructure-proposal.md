# Cambio de infraestructura propuesto (solo archivos locales)

La Lambda Python tiene las políticas administradas `AWSLambdaBasicExecutionRole`
y `AWSLambdaSQSQueueExecutionRole`. Permiten logs/SQS sobre `Resource = "*"`.
Se propone sustituir sus dos attachments por una política inline:

```hcl
resource "aws_iam_role_policy" "notification_runtime" {
  name = "${local.name}-notification-runtime"
  role = aws_iam_role.notification_lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.notification_lambda.arn}:*"
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
        Resource = aws_sqs_queue.notifications.arn
      }
    ]
  })
}
```

Se conserva la política SES existente, limitada a `ses:SendEmail` y a la
identidad del remitente en `us-east-2`. La Lambda esperará la nueva política
inline y el log group; no necesita `logs:CreateLogGroup`, porque Terraform
ya administra ese grupo. No se otorgan permisos de KMS: las colas actuales
no utilizan una clave KMS administrada por el usuario.

La Lambda Java esperará también `aws_iam_role_policy.backend_sns` y
`aws_sns_topic_subscription.notifications`, para que el flujo de publicación
esté preparado antes de actualizar el backend. Se mantienen sus permisos y
las rutas actuales.

Implicación del futuro plan: crear una política inline y retirar dos
attachments IAM, sin reemplazar roles, Lambdas, SNS ni colas. El verificador
del plan reconocerá únicamente esas dos retiradas, con la nueva política
presente; rechazará cualquier otra eliminación de infraestructura salvo el
reemplazo del objeto JAR. Estos cambios requieren autorización del usuario
antes de editar los archivos Terraform. No se ejecutará `apply`.
