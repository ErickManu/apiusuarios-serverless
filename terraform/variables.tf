variable "aws_region" {
  description = "AWS region for every regional resource."
  type        = string
  default     = "us-east-2"
}

variable "project_name" {
  description = "Short lowercase project name, also used in unique S3 bucket prefixes."
  type        = string
  default     = "apiusuarios"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,16}[a-z0-9]$", var.project_name))
    error_message = "project_name must contain 2-18 lowercase letters, digits or hyphens; start with a letter and end with a letter or digit."
  }
}

variable "stage_name" {
  description = "API Gateway stage and resource name suffix."
  type        = string
  default     = "dev"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{0,9}$", var.stage_name))
    error_message = "stage_name must contain 1-10 lowercase letters or digits, starting with a letter."
  }
}

variable "lambda_artifact_path" {
  description = "Path to the prebuilt Lambda JAR, relative to the terraform directory. Terraform does not build the backend."
  type        = string
  default     = "../target/apiusuarios-0.0.1-SNAPSHOT-lambda.jar"
}

variable "lambda_memory_size" {
  description = "Lambda memory in MiB. Java startup benefits from the accompanying CPU allocation."
  type        = number
  default     = 1024

  validation {
    condition     = var.lambda_memory_size >= 128 && var.lambda_memory_size <= 10240 && floor(var.lambda_memory_size) == var.lambda_memory_size
    error_message = "lambda_memory_size must be an integer between 128 and 10240 MiB."
  }
}

variable "lambda_timeout" {
  description = "Lambda timeout in seconds; bounded below the REST API integration's 29-second timeout."
  type        = number
  default     = 28

  validation {
    condition     = var.lambda_timeout >= 1 && var.lambda_timeout <= 28 && floor(var.lambda_timeout) == var.lambda_timeout
    error_message = "lambda_timeout must be an integer between 1 and 28 seconds."
  }
}

variable "log_retention_days" {
  description = "Retention period for the Lambda CloudWatch log group."
  type        = number
  default     = 14

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365], var.log_retention_days)
    error_message = "Choose a supported CloudWatch retention period between 1 and 365 days."
  }
}

variable "database_pool_size" {
  description = "Maximum JDBC connections per Lambda execution environment."
  type        = number
  default     = 2

  validation {
    condition     = var.database_pool_size >= 1 && floor(var.database_pool_size) == var.database_pool_size
    error_message = "database_pool_size must be a positive integer."
  }
}

variable "database_url" {
  description = "Neon PostgreSQL JDBC URL, normally with sslmode=require. Supply through TF_VAR_database_url or the ignored terraform.tfvars."
  type        = string
  sensitive   = true
  nullable    = false

  validation {
    condition     = startswith(var.database_url, "jdbc:postgresql://")
    error_message = "database_url must be a PostgreSQL JDBC URL starting with jdbc:postgresql://."
  }
}

variable "database_username" {
  description = "Neon PostgreSQL username."
  type        = string
  sensitive   = true
  nullable    = false

  validation {
    condition     = length(trimspace(var.database_username)) > 0
    error_message = "database_username must not be empty."
  }
}

variable "database_password" {
  description = "Neon PostgreSQL password. Never commit it to version control."
  type        = string
  sensitive   = true
  nullable    = false

  validation {
    condition     = length(var.database_password) > 0
    error_message = "database_password must not be empty."
  }
}

variable "jwt_secret" {
  description = "JWT signing secret, at least 32 characters. Reuse the backend's configured key to preserve existing token validity."
  type        = string
  sensitive   = true
  nullable    = false

  validation {
    condition     = length(var.jwt_secret) >= 32 && length(trimspace(var.jwt_secret)) > 0
    error_message = "jwt_secret must contain at least 32 characters and must not be blank."
  }
}
