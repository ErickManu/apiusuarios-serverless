
data "archive_file" "notification_lambda" {
  type        = "zip"
  source_file = "${path.module}/../notification-lambda/lambda_function.py"
  output_path = "${path.module}/notification-lambda.zip"
}

resource "aws_iam_role" "notification_lambda" {
  name = "${local.name}-notification-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

# Phase 1: keep the existing attachments until the scoped policy is verified.
# Preserve their original Terraform addresses; do not use count or rename them.
resource "aws_iam_role_policy_attachment" "notification_basic" {
  role       = aws_iam_role.notification_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "notification_sqs" {
  role       = aws_iam_role.notification_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaSQSQueueExecutionRole"
}

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

data "aws_caller_identity" "current" {}

resource "aws_iam_role_policy" "notification_ses" {
  name = "${local.name}-notification-ses-policy"
  role = aws_iam_role.notification_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ses:SendEmail"]
      Resource = "arn:aws:ses:${var.aws_region}:${data.aws_caller_identity.current.account_id}:identity/${var.notification_sender_email}"
    }]
  })
}

resource "aws_cloudwatch_log_group" "notification_lambda" {
  name              = "/aws/lambda/${local.name}-notification-lambda"
  retention_in_days = 7
}

resource "aws_lambda_function" "notification_lambda" {
  function_name = "${local.name}-notification-lambda"
  role          = aws_iam_role.notification_lambda.arn

  filename         = data.archive_file.notification_lambda.output_path
  source_code_hash = data.archive_file.notification_lambda.output_base64sha256

  handler = "lambda_function.lambda_handler"
  runtime = "python3.12"
  timeout = 30

  environment {
    variables = {
      SENDER_EMAIL = var.notification_sender_email
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.notification_basic,
    aws_iam_role_policy_attachment.notification_sqs,
    aws_iam_role_policy.notification_runtime,
    aws_iam_role_policy.notification_ses,
    aws_cloudwatch_log_group.notification_lambda
  ]
}

resource "aws_lambda_event_source_mapping" "notification_sqs" {
  event_source_arn = aws_sqs_queue.notifications.arn
  function_name    = aws_lambda_function.notification_lambda.arn

  batch_size = 5

  function_response_types = [
    "ReportBatchItemFailures"
  ]
}
