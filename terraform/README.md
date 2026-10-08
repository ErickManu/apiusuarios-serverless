# Infraestructura de apiusuarios

Arquitectura: **API Gateway REST API -> Lambda Java 21 -> Neon PostgreSQL**.
Los archivos de la aplicación se almacenan en un bucket S3 privado.

Notificaciones: **Lambda Java → SNS → SQS (con DLQ) → Lambda Python → SES**.
La [revisión de notificaciones](../docs/notification-review.md) documenta las
correcciones, evidencias sobre el tema eliminado, protección y pendientes.

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
estado de Terraform ni de un plan guardado**. El backend remoto S3 está definido
en `backend.tf`, sin credenciales AWS:

- Bucket existente: `apiusuarios-tfstate-625228299719-us-east-2`.
- Key: `apiusuarios/dev/terraform.tfstate`.
- Región: `us-east-2`.
- Cifrado: `encrypt = true`.
- Bloqueo nativo S3: `use_lockfile = true`, con Terraform >= 1.10.

El bucket de estado fue creado manualmente y no está administrado por este módulo.
Es independiente de los dos buckets de la aplicación. Los respaldos locales,
planes y tfvars permanecen ignorados por Git; el estado contiene datos sensibles.

La identidad que ejecute Terraform necesita `s3:ListBucket` sobre el bucket de
estado, `s3:GetObject` y `s3:PutObject` sobre la key del estado, y
`s3:GetObject`, `s3:PutObject` y `s3:DeleteObject` sobre
`apiusuarios/dev/terraform.tfstate.tflock`. Son permisos de la identidad de
Terraform, no del rol de ejecución de Lambda.

## Comprobaciones sin despliegue

Comprobaciones locales, con los proveedores ya instalados:

```powershell
terraform fmt -check -recursive
terraform validate
terraform test -no-color
```

Las pruebas usan proveedores simulados y `command = plan`: no acceden a AWS ni
aplican recursos. Para inspeccionar infraestructura remota más adelante, con
Terraform >= 1.10 y < 2, AWS CLI configurado y el JAR ya compilado:

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

Para migrar el estado local existente se ejecutó `terraform init -migrate-state`
y se confirmó la copia del estado a S3. Se conservó un respaldo local ignorado
por Git en `terraform.tfstate.pre-s3.backup`.

En otros equipos y en el futuro workflow de GitHub Actions se debe ejecutar
`terraform init -input=false`, sin volver a migrar ni crear un estado vacío.
Usar el workspace `default`, la misma configuración del backend y los valores
reales `TF_VAR_*` de la infraestructura desplegada. No reutilizar un plan guardado
antes de la migración; generar siempre uno nuevo después de inicializar el backend.
El workflow de GitHub Actions es exclusivamente manual (`workflow_dispatch`),
con `desplegar` desactivado por defecto. Su verificador del plan bloquea
eliminaciones inesperadas antes del apply condicionado a ese booleano.

Los buckets tienen `force_destroy=false`: Terraform no vacía automáticamente
los archivos para destruirlos. Los datos locales previos no se transfieren a S3.
El output `api_base_url` incluye el stage; las rutas existentes se añaden a esa URL.
