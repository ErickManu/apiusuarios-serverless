# Backend en AWS Lambda

Se conservan Spring Boot **4.1.1**, Java **21**, las capas `application`, `domain` e
`infrastructure`, los controllers y la autenticación JWT de la aplicación.
Se utiliza la integración oficial `aws-serverless-java-container-springboot4:3.0.2`.
El SDK de S3 es AWS SDK for Java v2, fijado mediante su BOM a `2.35.0`.

## Compilación

Desde la raíz de este backend, con JDK 21:

```powershell
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd -B -ntp -Plambda verify
```

El primer comando genera el JAR ejecutable local `target/apiusuarios-0.0.1-SNAPSHOT.jar`.
El segundo genera `target/apiusuarios-0.0.1-SNAPSHOT-lambda.jar`, un JAR con dependencias
y clases en la raíz, apto para el runtime Java de Lambda. **Subir el JAR con sufijo
`-lambda`**, no el JAR local con dependencias anidadas en `BOOT-INF`.
El artefacto validado ocupa aproximadamente 62 MiB comprimido y 156 MiB
descomprimido. Supera el límite de carga directa de 50 MB: para desplegarlo,
utilizar un objeto S3 como origen del código de Lambda. Esto es independiente
del bucket de archivos de la aplicación. Está por debajo del límite de 250 MB
descomprimidos, que también incluye las layers si se añaden posteriormente.

El perfil Maven `lambda` selecciona el empaquetado. El perfil Spring `lambda`
selecciona la configuración en ejecución y lo activa el handler automáticamente.
Para volver a generar el JAR ejecutable local después de empaquetar Lambda,
ejecutar de nuevo el primer comando.

En la sesión de implementación se utilizó una caché Maven dentro de `target` para
evitar escrituras fuera del proyecto:

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) 'target\maven-user'
.\mvnw.cmd '-Dmaven.repo.local=target/maven-repository' -B -ntp verify
.\mvnw.cmd '-Dmaven.repo.local=target/maven-repository' -B -ntp -Plambda verify
```

No usar `clean` con esa ubicación de caché: eliminaría también Maven y sus
dependencias durante la ejecución. Los comandos normales pueden usar la caché
habitual del usuario.

## Ejecución local

Sin el perfil Spring `lambda`, el almacenamiento sigue siendo el directorio
`uploads` y la lectura conserva `/uploads/**`. No se crea un cliente S3 ni se
requieren credenciales AWS. `UPLOAD_DIRECTORY` permite cambiar esa carpeta.

Antes de arrancar, proporcionar en el entorno:

- `DATABASE_URL`: URL JDBC de PostgreSQL. Por defecto, localmente se utiliza
  `jdbc:postgresql://localhost:5432/api_usuarios`.
- `DATABASE_USERNAME` y `DATABASE_PASSWORD`: credenciales de esa base de datos.
- `JWT_SECRET`: clave de firma, de al menos 32 bytes UTF-8.

También se admiten los overrides estándar `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` de Spring.
Una URL `postgres://...` no es una URL JDBC; usar `jdbc:postgresql://host:puerto/base`.

```powershell
.\mvnw.cmd spring-boot:run
```

La aplicación no carga automáticamente un archivo `.env` al arrancar desde Java
o el IDE: configurar las variables en el proceso o en la configuración de ejecución.
Docker Compose sí utiliza `.env` para interpolar sus variables. El Compose existente
conserva PostgreSQL, el volumen de archivos y los puertos, y ahora transmite
`JWT_SECRET` al backend. Configurar `DB_PASSWORD` y `JWT_SECRET` antes de:

```powershell
docker compose up --build
```

La clave JWT ya no tiene un valor fijo en código ni un valor de respaldo. Si se
reutiliza la misma clave externa, los tokens existentes mantienen su validez hasta
su vencimiento; cambiarla exige iniciar sesión de nuevo. Se conservan el subject,
la firma mediante JJWT y la duración de una hora.

## Configuración de Lambda

- Runtime: **java21**.
- Handler: `com.erick.apiusuarios.infrastructure.lambda.StreamLambdaHandler::handleRequest`.
- API Gateway: **REST API**, integración **Lambda proxy**, payload **1.0**.
- Perfil Spring: `lambda`, activado por el handler.
- El contexto de Spring, el pool JDBC y el cliente S3 se reutilizan entre
  invocaciones del mismo entorno. No se usan sesiones HTTP.

Variables obligatorias:

| Variable | Uso |
| --- | --- |
| `DATABASE_URL` | URL JDBC de PostgreSQL accesible desde Lambda. |
| `DATABASE_USERNAME` | Usuario PostgreSQL. |
| `DATABASE_PASSWORD` | Contraseña PostgreSQL. |
| `JWT_SECRET` | Clave JWT externa, al menos 32 bytes UTF-8. |
| `S3_BUCKET` | Nombre del bucket privado, sin `s3://`. |
| `AWS_REGION` | Región AWS; Lambda la proporciona automáticamente. |

Las credenciales de AWS se resuelven mediante `DefaultCredentialsProvider`, que
utiliza las credenciales temporales del rol de ejecución en Lambda. No almacenar
access keys en propiedades ni en el código. Los secretos de PostgreSQL/JWT deben
inyectarse desde la configuración de despliegue; el backend no crea ni administra
secretos en AWS.

Opcionales:

| Variable | Valor por defecto |
| --- | --- |
| `DATABASE_POOL_SIZE` | `2` conexiones por entorno Lambda |
| `DATABASE_CONNECTION_TIMEOUT_MS` | `10000` |
| `UPLOAD_MAX_FILE_SIZE` | `1MB` |
| `UPLOAD_MAX_REQUEST_SIZE` | `4MB` |

El tamaño total de los pools crece con la concurrencia de Lambda. Ajustarlo con
los límites de PostgreSQL; un RDS Proxy sería una decisión posterior de infraestructura.
El perfil Lambda utiliza `ddl-auto=validate`, no crea ni actualiza el esquema:
las tablas `usuarios` y `archivos` deben existir y ser compatibles con las entidades.
El perfil local conserva `ddl-auto=update`.

## API Gateway, archivos y contrato REST

Se conservan:

- `POST /auth/login`, con respuesta `{ "token": "..." }`.
- `GET/POST /usuarios` y `GET/PUT/DELETE /usuarios/{id}`.
- `GET /datos/perfil`, `/datos/configuracion` y `/datos/estado`.
- `POST /upload`, multipart con campo `archivo`.
- `GET /uploads/{nombreGuardado}`.

La subida conserva los campos `id`, `nombre`, `nombreGuardado`, `url` y `mensaje`.
La URL sigue siendo relativa: `/uploads/{nombreGuardado}`. En Lambda los bytes
se guardan en S3 y los metadatos siguen en PostgreSQL. La descarga pasa por el
backend y conserva la autenticación JWT; el bucket no necesita ser público.

Requisitos de infraestructura a aplicar en una etapa posterior:

1. Enviar las rutas necesarias mediante integración proxy, habitualmente
   `ANY /{proxy+}` y `ANY /`, y conservar `Authorization`, cabeceras múltiples y body.
2. Configurar **binary media types `*/*`** en la REST API. El handler responde con
   Base64 y `isBase64Encoded` cuando corresponde; API Gateway lo decodifica antes
   de responder al cliente. Esto incluye JSON, texto y archivos. El multipart
   binario debe llegar con `isBase64Encoded=true`. No aplicar plantillas que lo alteren.
3. Permitir que `OPTIONS` llegue al backend para usar el CORS existente, o reproducir
   esa política en el gateway. La lista de orígenes local/Capacitor no se amplió.
4. Proporcionar acceso de red a PostgreSQL y S3. En una VPC, resolver rutas/endpoints,
   DNS y reglas de seguridad según la ubicación de esos servicios.
5. Conceder al rol `s3:PutObject` y `s3:GetObject` para los objetos del bucket.
   Para que S3 distinga un objeto ausente con 404, conceder `s3:ListBucket` sobre
   el bucket según la política aplicable. Un 403 de S3 se trata como fallo del
   almacenamiento, no como archivo inexistente. Si se usa SSE-KMS, configurar
   también los permisos de la clave.
6. Trasladar los archivos locales previos a las claves raíz del bucket conservando
   exactamente `nombreGuardado`. El código no migra ni elimina archivos existentes.

Lambda limita a 6 MB los payloads de solicitud/respuesta síncronos con buffering.
Base64 aumenta el tamaño aproximadamente un tercio, además del evento JSON.
Los límites por defecto dejan margen; no aumentarlos sin considerar ambos límites.
Las descargas antiguas de mayor tamaño también quedan sujetas a ese límite.
`/tmp` se utiliza solo para temporales multipart; no es almacenamiento persistente.

Este handler no acepta payload HTTP API v2.0. Ese tipo de gateway requiere configurar
la variante correspondiente del adaptador y pruebas específicas antes del despliegue.
No se crean recursos AWS ni se modifican Terraform, GitHub Actions o UsuariosMobile.

## Pruebas y alcance de la validación

Las pruebas Maven ejecutan:

- Arranque local sin S3 y subida/descarga real en una carpeta aislada bajo `target`.
- Eventos proxy contra el handler real, Spring MVC, Spring Security y JPA: login,
  CRUD, endpoints de datos, CORS, aislamiento de autenticación, multipart y binarios.
- Adaptador S3: nombres, bytes, metadatos y errores de escritura/lectura.
- Rechazo HTTP 413 de archivos que exceden el máximo configurado, antes de S3.

El servlet del adaptador no aplica automáticamente los límites multipart de
Tomcat. El controller comprueba las mismas propiedades de tamaño antes de guardar.

La base de pruebas es **H2 en modo PostgreSQL**. El cliente S3 se simula. Los tests
generan claves y credenciales de prueba, no utilizan secretos reales ni conectan
con la base de datos del usuario. H2 no sustituye una prueba contra PostgreSQL.
Docker no era accesible desde la sesión de implementación; la prueba contra
PostgreSQL real y la validación desplegada en API Gateway/Lambda/S3 quedan pendientes.

Referencias:

- [AWS Serverless Java Container para Spring Boot 4](https://github.com/aws/serverless-java-container/wiki/Quick-start---Spring-Boot4)
- [Cuotas de AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/gettingstarted-limits.html)
- [Contenido binario de API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-payload-encodings.html)
