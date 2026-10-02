output "api_base_url" {
  description = "API Gateway base URL including the stage; append /auth/login, /usuarios or another existing route."
  value       = aws_api_gateway_stage.backend.invoke_url
}

output "api_gateway_id" {
  description = "REST API identifier."
  value       = aws_api_gateway_rest_api.backend.id
}

output "lambda_function_name" {
  description = "Backend Lambda function name."
  value       = aws_lambda_function.backend.function_name
}

output "lambda_function_arn" {
  description = "Backend Lambda function ARN."
  value       = aws_lambda_function.backend.arn
}

output "lambda_role_arn" {
  description = "Lambda execution role ARN."
  value       = aws_iam_role.lambda.arn
}

output "files_bucket_name" {
  description = "Private application files bucket, also passed to Lambda as S3_BUCKET."
  value       = aws_s3_bucket.storage["files"].id
}

output "code_bucket_name" {
  description = "Private bucket for the Lambda deployment JAR."
  value       = aws_s3_bucket.storage["code"].id
}

output "lambda_log_group_name" {
  description = "CloudWatch log group for the backend."
  value       = aws_cloudwatch_log_group.lambda.name
}
