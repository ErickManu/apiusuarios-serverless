
# Permitir que la Lambda principal publique en SNS
resource "aws_iam_role_policy" "backend_sns" {
  name = "${local.name}-sns-publish"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"

    Statement = [{
      Effect = "Allow"

      Action = [
        "sns:Publish"
      ]

      Resource = aws_sns_topic.notifications.arn
    }]
  })
}
