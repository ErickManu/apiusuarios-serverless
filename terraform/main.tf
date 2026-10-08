locals {
  name                 = "${var.project_name}-${var.stage_name}"
  lambda_artifact_path = abspath("${path.module}/${var.lambda_artifact_path}")
  artifact_sha256      = filesha256(local.lambda_artifact_path)
}

# bucket_prefix lets the provider generate a globally unique suffix.
# Separate application data from deployment code; Lambda can only access files.
resource "aws_s3_bucket" "storage" {
  for_each = toset(["files", "code"])

  bucket_prefix = "${local.name}-${each.key}-"
  force_destroy = false
}

resource "aws_s3_bucket_public_access_block" "storage" {
  for_each = aws_s3_bucket.storage

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "storage" {
  for_each = aws_s3_bucket.storage

  bucket = each.value.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "storage" {
  for_each = aws_s3_bucket.storage

  bucket = each.value.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

data "aws_iam_policy_document" "storage_tls" {
  for_each = aws_s3_bucket.storage

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [each.value.arn, "${each.value.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "storage_tls" {
  for_each = aws_s3_bucket.storage

  bucket = each.value.id
  policy = data.aws_iam_policy_document.storage_tls[each.key].json
}

# The JAR exceeds Lambda's direct upload limit. Upload through S3 on apply.
# A content-addressed key avoids reusing the code object when the JAR changes.
resource "aws_s3_object" "lambda_code" {
  bucket                 = aws_s3_bucket.storage["code"].id
  key                    = "lambda/${local.artifact_sha256}.jar"
  source                 = local.lambda_artifact_path
  source_hash            = local.artifact_sha256
  content_type           = "application/java-archive"
  server_side_encryption = "AES256"

  depends_on = [
    aws_s3_bucket_public_access_block.storage,
    aws_s3_bucket_ownership_controls.storage,
    aws_s3_bucket_server_side_encryption_configuration.storage,
    aws_s3_bucket_policy.storage_tls,
  ]
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${local.name}"
  retention_in_days = var.log_retention_days
}

data "aws_iam_policy_document" "lambda_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda" {
  name               = "${local.name}-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_trust.json
}

data "aws_iam_policy_document" "lambda_runtime" {
  statement {
    sid       = "WriteFunctionLogs"
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.lambda.arn}:*"]
  }

  statement {
    sid       = "ReadWriteApplicationFiles"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.storage["files"].arn}/*"]
  }

  # Required so a missing object returns 404 rather than AccessDenied (403).
  statement {
    sid       = "FindApplicationFiles"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.storage["files"].arn]
  }
}

resource "aws_iam_role_policy" "lambda_runtime" {
  name   = "${local.name}-runtime"
  role   = aws_iam_role.lambda.id
  policy = data.aws_iam_policy_document.lambda_runtime.json
}

resource "aws_lambda_function" "backend" {
  function_name    = local.name
  role             = aws_iam_role.lambda.arn
  runtime          = "java21"
  architectures    = ["x86_64"]
  handler          = "com.erick.apiusuarios.infrastructure.lambda.StreamLambdaHandler::handleRequest"
  memory_size      = var.lambda_memory_size
  timeout          = var.lambda_timeout
  s3_bucket        = aws_s3_object.lambda_code.bucket
  s3_key           = aws_s3_object.lambda_code.key
  source_code_hash = filebase64sha256(local.lambda_artifact_path)

  # No VPC: the managed Lambda network can reach public Neon PostgreSQL over TLS.

  environment {
    variables = {
      SPRING_PROFILES_ACTIVE = "lambda"
      DATABASE_URL           = var.database_url
      DATABASE_USERNAME      = var.database_username
      DATABASE_PASSWORD      = var.database_password
      JWT_SECRET             = var.jwt_secret
      DATABASE_POOL_SIZE     = tostring(var.database_pool_size)
      S3_BUCKET              = aws_s3_bucket.storage["files"].id

      # SNS para enviar notificaciones
      SNS_TOPIC_ARN = aws_sns_topic.notifications.arn
    }
  }


  depends_on = [
    aws_iam_role_policy.lambda_runtime,
    aws_iam_role_policy.backend_sns,
    aws_sns_topic_subscription.notifications,
  ]
}

resource "aws_api_gateway_rest_api" "backend" {
  name        = local.name
  description = "REST proxy for the apiusuarios Spring Boot backend"

  binary_media_types = ["*/*"]

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_api_gateway_resource" "proxy" {
  rest_api_id = aws_api_gateway_rest_api.backend.id
  parent_id   = aws_api_gateway_rest_api.backend.root_resource_id
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_method" "proxy" {
  for_each = {
    root  = aws_api_gateway_rest_api.backend.root_resource_id
    proxy = aws_api_gateway_resource.proxy.id
  }

  rest_api_id   = aws_api_gateway_rest_api.backend.id
  resource_id   = each.value
  http_method   = "ANY"
  authorization = "NONE" # Existing Spring Security validates JWT; OPTIONS reaches CORS.
}

resource "aws_api_gateway_integration" "lambda" {
  for_each = aws_api_gateway_method.proxy

  rest_api_id             = aws_api_gateway_rest_api.backend.id
  resource_id             = each.value.resource_id
  http_method             = each.value.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.backend.invoke_arn
  timeout_milliseconds    = 29000
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowApiGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.backend.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.backend.execution_arn}/${var.stage_name}/*/*"
}

resource "aws_api_gateway_deployment" "backend" {
  rest_api_id = aws_api_gateway_rest_api.backend.id

  triggers = {
    redeployment = sha1(jsonencode({
      binary_media_types = aws_api_gateway_rest_api.backend.binary_media_types
      routes = {
        for name, integration in aws_api_gateway_integration.lambda : name => {
          resource_id             = integration.resource_id
          http_method             = integration.http_method
          integration_http_method = integration.integration_http_method
          type                    = integration.type
          uri                     = integration.uri
          timeout_milliseconds    = integration.timeout_milliseconds
          authorization           = aws_api_gateway_method.proxy[name].authorization
        }
      }
    }))
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [aws_lambda_permission.api_gateway]
}

resource "aws_api_gateway_stage" "backend" {
  rest_api_id   = aws_api_gateway_rest_api.backend.id
  deployment_id = aws_api_gateway_deployment.backend.id
  stage_name    = var.stage_name
}
