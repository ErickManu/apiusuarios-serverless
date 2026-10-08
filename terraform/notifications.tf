

resource "aws_sns_topic" "notifications" {
  name = "${local.name}-notifications"

  lifecycle {
    prevent_destroy = true
  }
}



# Cola para mensajes que fallan repetidamente
resource "aws_sqs_queue" "notifications_dlq" {
  name = "${local.name}-notifications-dlq"

  message_retention_seconds = 1209600
}

# Cola principal de notificaciones
resource "aws_sqs_queue" "notifications" {
  name = "${local.name}-notifications-queue"

  visibility_timeout_seconds = 180

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.notifications_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue_policy" "notifications" {
  queue_url = aws_sqs_queue.notifications.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.notifications.arn
      Condition = {
        ArnEquals = {
          "aws:SourceArn" = aws_sns_topic.notifications.arn
        }
      }
    }]
  })
}

resource "aws_sns_topic_subscription" "notifications" {
  topic_arn = aws_sns_topic.notifications.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.notifications.arn

  raw_message_delivery = true

  depends_on = [
    aws_sqs_queue_policy.notifications
  ]
}
