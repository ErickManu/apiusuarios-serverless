# Plan with mocked providers: no AWS calls, remote state or real resources.
mock_provider "aws" {
  override_during = plan
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}
mock_provider "archive" {
  override_during = plan
}

override_data {
  target = data.aws_caller_identity.current
  values = {
    account_id = "000000000000"
  }
  override_during = plan
}

override_resource {
  target          = aws_sns_topic.notifications
  override_during = plan
  values = {
    arn = "arn:aws:sns:us-east-2:000000000000:apiusuarios-dev-notifications"
  }
}

override_resource {
  target          = aws_sqs_queue.notifications
  override_during = plan
  values = {
    arn = "arn:aws:sqs:us-east-2:000000000000:apiusuarios-dev-notifications-queue"
  }
}

override_resource {
  target          = aws_sqs_queue.notifications_dlq
  override_during = plan
  values = {
    arn = "arn:aws:sqs:us-east-2:000000000000:apiusuarios-dev-notifications-dlq"
  }
}

override_resource {
  target          = aws_cloudwatch_log_group.notification_lambda
  override_during = plan
  values = {
    arn = "arn:aws:logs:us-east-2:000000000000:log-group:/aws/lambda/apiusuarios-dev-notification-lambda"
  }
}

variables {
  aws_region                = "us-east-2"
  project_name              = "apiusuarios"
  stage_name                = "dev"
  database_url              = "jdbc:postgresql://localhost/offline"
  database_username         = "offline"
  database_password         = "offline-test-only"
  jwt_secret                = "offline-test-only-key-with-at-least-32-characters"
  notification_sender_email = "sender@example.invalid"
  # Only a local hash fixture: provider mocks never upload or execute this file.
  lambda_artifact_path = "../pom.xml"
}

run "notification_contract" {
  command = plan

  assert {
    condition     = aws_sns_topic.notifications.name == "apiusuarios-dev-notifications"
    error_message = "Keep the existing topic name."
  }

  assert {
    condition = (
      aws_sns_topic_subscription.notifications.raw_message_delivery &&
      aws_sns_topic_subscription.notifications.protocol == "sqs" &&
      aws_sns_topic_subscription.notifications.endpoint == aws_sqs_queue.notifications.arn
    )
    error_message = "Python expects raw JSON delivered to the notification queue."
  }

  assert {
    condition = (
      aws_sqs_queue.notifications.visibility_timeout_seconds >= 6 * aws_lambda_function.notification_lambda.timeout &&
      # Mock providers skip AWS defaults; SQS defaults to four days (345600s).
      aws_sqs_queue.notifications_dlq.message_retention_seconds > coalesce(aws_sqs_queue.notifications.message_retention_seconds, 345600) &&
      jsondecode(aws_sqs_queue.notifications.redrive_policy).deadLetterTargetArn == aws_sqs_queue.notifications_dlq.arn &&
      jsondecode(aws_sqs_queue.notifications.redrive_policy).maxReceiveCount == 3
    )
    error_message = "Keep the queue visibility timeout and DLQ redrive contract."
  }

  assert {
    condition = (
      aws_lambda_event_source_mapping.notification_sqs.batch_size == 5 &&
      contains(aws_lambda_event_source_mapping.notification_sqs.function_response_types, "ReportBatchItemFailures") &&
      aws_lambda_event_source_mapping.notification_sqs.event_source_arn == aws_sqs_queue.notifications.arn
    )
    error_message = "Enable partial SQS batch responses for the notification Lambda."
  }

  assert {
    condition = (
      jsondecode(aws_iam_role_policy.backend_sns.policy).Statement[0].Action == ["sns:Publish"] &&
      jsondecode(aws_iam_role_policy.backend_sns.policy).Statement[0].Resource == aws_sns_topic.notifications.arn &&
      aws_lambda_function.backend.environment[0].variables.SNS_TOPIC_ARN == aws_sns_topic.notifications.arn
    )
    error_message = "Backend publication and environment must reference the same topic."
  }

  assert {
    condition = (
      jsondecode(aws_iam_role_policy.notification_runtime.policy).Statement[0].Resource == "${aws_cloudwatch_log_group.notification_lambda.arn}:*" &&
      jsondecode(aws_iam_role_policy.notification_runtime.policy).Statement[1].Resource == aws_sqs_queue.notifications.arn &&
      jsondecode(aws_iam_role_policy.notification_runtime.policy).Statement[1].Action == ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"] &&
      jsondecode(aws_iam_role_policy.notification_ses.policy).Statement[0].Resource == "arn:aws:ses:us-east-2:000000000000:identity/sender@example.invalid" &&
      jsondecode(aws_iam_role_policy.notification_ses.policy).Statement[0].Action == ["ses:SendEmail"]
    )
    error_message = "Notification IAM must be scoped to the queue, log group and SES identity."
  }

  assert {
    condition = (
      jsondecode(aws_sqs_queue_policy.notifications.policy).Statement[0].Principal.Service == "sns.amazonaws.com" &&
      jsondecode(aws_sqs_queue_policy.notifications.policy).Statement[0].Action == "sqs:SendMessage" &&
      jsondecode(aws_sqs_queue_policy.notifications.policy).Statement[0].Resource == aws_sqs_queue.notifications.arn &&
      jsondecode(aws_sqs_queue_policy.notifications.policy).Statement[0].Condition.ArnEquals["aws:SourceArn"] == aws_sns_topic.notifications.arn
    )
    error_message = "Only the existing SNS topic should deliver to the queue."
  }
}
