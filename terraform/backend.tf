terraform {
  backend "s3" {
    bucket       = "apiusuarios-tfstate-625228299719-us-east-2"
    key          = "apiusuarios/dev/terraform.tfstate"
    region       = "us-east-2"
    encrypt      = true
    use_lockfile = true
  }
}
