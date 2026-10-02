# Infraestructura de apiusuarios

Arquitectura: **API Gateway REST API -> Lambda Java 21 -> Neon PostgreSQL**.
Los archivos de la aplicación se almacenan en un bucket S3 privado.

## Recursos

- Lambda con el handler Spring Boot existente, perfil `lambda`, 1024 MiB y timeout
  de 28 segundos por defecto.
- REST API regional en `us-east-2`, `ANY /` y `ANY /{proxy+}`, integración
  `AWS_PROXY` (payload REST 1.0), deployment y stage `dev`.
- Tipos binarios `*/*` para preservar multipart, imágenes y las respuestas Base64
  del adaptador. JWT y CORS siguen siendo responsabilidad del backend. No se
  añaden authorizers ni plantillas que transformen sus respuestas.
- Rol IAM exclusivo de Lambda con permisos para escribir en su log group y
  `GetObject`, `PutObject`, `ListBucket` únicamente en el bucket de archivos.
- Log group CloudWatch con retención configurable (14 días por defecto).
- Bucket de archivos y bucket separado para el artefacto de código. Ambos tienen
  nombres únicos generados mediante `bucket_prefix`, bloqueo de acceso público,
  ACL deshabilitadas, cifrado SSE-S3 y política que exige HTTPS.
- Objeto S3 con el JAR. El nombre de su clave incluye el SHA-256 del artefacto;
  cambiar el JAR provoca la actualización de Lambda.
- Permiso de invocación para API Gateway, limitado a esta API y su stage.

El segundo bucket es necesario porque el JAR actual supera los 50 MB permitidos
para carga directa del código de Lambda. La carga del objeto S3 se realiza solo
al aplicar, nunca durante `fmt`, `init`, `validate` o `plan`.

No se crean VPC, NAT Gateway ni RDS: Lambda utiliza su red administrada para
conectarse al endpoint público de Neon mediante TLS. Neon debe admitir esa
conectividad y su esquema debe existir: el backend usa `ddl-auto=validate`.
Terraform no modifica Neon, el esquema, los usuarios ni los archivos existentes.

## Credenciales y configuración

El provider utiliza la cadena estándar de credenciales AWS, incluyendo la
configuración de AWS CLI. Se puede seleccionar otro perfil con `AWS_PROFILE`.
No hay credenciales AWS en los archivos Terraform.

`terraform.tfvars` contiene únicamente configuración no secreta y está ignorado
por Git. `terraform.tfvars.example` se puede versionar.

Las cuatro variables privadas son obligatorias y están marcadas `sensitive`:

| Entrada Terraform | Variable Lambda |
| --- | --- |
| `database_url` / `TF_VAR_database_url` | `DATABASE_URL` |
| `database_username` / `TF_VAR_database_username` | `DATABASE_USERNAME` |
| `database_password` / `TF_VAR_database_password` | `DATABASE_PASSWORD` |
| `jwt_secret` / `TF_VAR_jwt_secret` | `JWT_SECRET` |

La URL debe tener formato JDBC: `jdbc:postgresql://HOST/BASE?sslmode=require`,
no `postgres://`. Proporcionar los valores mediante variables `TF_VAR_*` en el
proceso que ejecute Terraform, o mediante el archivo local ignorado.
No copiar secretos al archivo de ejemplo.

Terraform asigna `S3_BUCKET` automáticamente. `AWS_REGION` la proporciona Lambda
automáticamente y no se declara en `environment`, porque AWS la reserva.
`SPRING_PROFILES_ACTIVE` se configura como `lambda`.

`sensitive` oculta los valores en la salida habitual, pero **no los elimina del
estado de Terraform ni de un plan guardado**. Esta configuración usa estado local;
los archivos de estado, planes y tfvars están ignorados por Git. Protegerlos y,
antes de un uso compartido, definir un backend de estado con acceso controlado.

## Comprobaciones sin despliegue

Con Terraform >= 1.5 y < 2, AWS CLI configurado y el JAR ya compilado:

```powershell
Set-Location 'C:\Users\Erick\OneDrive\Documentos\apiusuarios\apiusuarios\terraform'
terraform fmt
terraform init -input=false
terraform validate
# Solo cuando las cuatro variables privadas estén disponibles:
terraform plan -input=false
```

`terraform init` genera `.terraform.lock.hcl`, que debe versionarse; `.terraform/`
contiene los proveedores descargados y se ignora. No se ejecuta `apply` en esta etapa.

El plan de comprobación de esta etapa utiliza valores ficticios temporales para
las cuatro entradas privadas, sin guardarlos en tfvars ni generar un archivo de
plan aplicable. No comprueba credenciales de Neon, conectividad de la base de datos
ni el arranque de Lambda. Antes del despliegue se necesita un nuevo plan con los
valores correctos.

Los buckets tienen `force_destroy=false`: Terraform no vacía automáticamente
los archivos para destruirlos. Los datos locales previos no se transfieren a S3.
El output `api_base_url` incluye el stage; las rutas existentes se añaden a esa URL.
