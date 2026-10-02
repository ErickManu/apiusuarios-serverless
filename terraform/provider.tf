# Uses the standard AWS credential chain (AWS CLI profile/environment).
# No access keys, secret keys or credential files are managed by Terraform.
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.stage_name
      ManagedBy   = "Terraform"
    }
  }
}
