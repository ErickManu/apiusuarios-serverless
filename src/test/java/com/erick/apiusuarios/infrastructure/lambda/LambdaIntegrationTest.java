package com.erick.apiusuarios.infrastructure.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.erick.apiusuarios.ApiusuariosApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real API Gateway proxy events, Spring MVC, Security and JPA; H2 and simulated S3. */
class LambdaIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, ResponseBytes<GetObjectResponse>> OBJECTS = new ConcurrentHashMap<>();
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static StreamLambdaHandler handler;
    private static Context context;

    @TestConfiguration(proxyBeanMethods = false)
    @Import(ApiusuariosApplication.class)
    static class TestApplication {
        @Bean
        @Primary
        S3Client simulatedS3() {
            S3Client client = mock(S3Client.class);
            when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
                PutObjectRequest request = call.getArgument(0);
                RequestBody body = call.getArgument(1);
                try (var input = body.contentStreamProvider().newStream()) {
                    OBJECTS.put(request.key(), ResponseBytes.fromByteArray(
                            GetObjectResponse.builder().contentType(request.contentType()).build(), input.readAllBytes()));
                }
                return PutObjectResponse.builder().eTag("test-etag").build();
            });
            when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(call -> {
                GetObjectRequest request = call.getArgument(0);
                var object = OBJECTS.get(request.key());
                if (object == null) throw NoSuchKeyException.builder().statusCode(404).build();
                return object;
            });
            return client;
        }
    }

    @BeforeAll
    static void initialize() throws Exception {
        Files.createDirectories(Path.of("target/test-multipart"));
        context = mock(Context.class);
        when(context.getAwsRequestId()).thenReturn("test-request");
        when(context.getRemainingTimeInMillis()).thenReturn(30000);
        when(context.getLogger()).thenReturn(mock(LambdaLogger.class));
        handler = new StreamLambdaHandler(TestApplication.class, "lambda", "test");
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:apiusuarios_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "");
             var statement = connection.prepareStatement("insert into usuarios(nombre,email,password) values (?,?,?)")) {
            statement.setString(1, "Lambda test");
            statement.setString(2, "lambda-test@example.invalid");
            statement.setString(3, new BCryptPasswordEncoder().encode(PASSWORD));
            statement.executeUpdate();
        }
    }

    @Test
    void loginAndAllDatosEndpointsWorkThroughTheHandler() throws Exception {
        String token = login();
        for (String path : new String[]{"/datos/perfil", "/datos/configuracion", "/datos/estado"}) {
            var result = request("GET", path, null, token, "application/json", false, Map.of());
            assertThat(result.path("statusCode").asInt()).isEqualTo(200);
            assertThat(JSON.readTree(body(result)).isObject()).isTrue();
        }
    }

    @Test
    void authenticationDoesNotLeakBetweenWarmInvocations() throws Exception {
        assertThat(request("GET", "/usuarios", null, login(), "application/json", false, Map.of())
                .path("statusCode").asInt()).isEqualTo(200);
        assertThat(request("GET", "/usuarios", null, null, "application/json", false, Map.of())
                .path("statusCode").asInt()).isIn(401, 403);
        assertThat(request("GET", "/usuarios", null, "invalid-token", "application/json", false, Map.of())
                .path("statusCode").asInt()).isIn(401, 403);
    }

    @Test
    void userCrudPreservesItsRoutesAndStatusCodes() throws Exception {
        String token = login();
        String email = UUID.randomUUID() + "@example.invalid";
        var created = request("POST", "/usuarios", JSON.writeValueAsString(Map.of(
                "nombre", "Inicial", "email", email, "password", PASSWORD)), token, "application/json", false, Map.of());
        assertThat(created.path("statusCode").asInt()).isEqualTo(201);
        var createdUser = JSON.readTree(body(created));
        assertPublicUser(createdUser);
        String path = "/usuarios/" + createdUser.path("id").asLong();
        String createdUserToken = login(email, PASSWORD);
        var found = request("GET", path, null, createdUserToken, "application/json", false, Map.of());
        assertThat(found.path("statusCode").asInt()).isEqualTo(200);
        assertPublicUser(JSON.readTree(body(found)));
        var listed = request("GET", "/usuarios", null, token, "application/json", false, Map.of());
        assertThat(listed.path("statusCode").asInt()).isEqualTo(200);
        var users = JSON.readTree(body(listed));
        assertThat(users.isArray()).isTrue();
        assertThat(users.size()).isPositive();
        users.forEach(LambdaIntegrationTest::assertPublicUser);
        var updated = request("PUT", path, JSON.writeValueAsString(Map.of("nombre", "Actualizado", "email", email)),
                token, "application/json", false, Map.of());
        assertThat(updated.path("statusCode").asInt()).isEqualTo(200);
        assertPublicUser(JSON.readTree(body(updated)));
        assertThat(JSON.readTree(body(updated)).path("nombre").asText()).isEqualTo("Actualizado");
        login(email, PASSWORD);
        String replacementPassword = UUID.randomUUID().toString();
        var passwordChanged = request("PUT", path, JSON.writeValueAsString(Map.of(
                        "nombre", "Actualizado", "email", email, "password", replacementPassword)),
                token, "application/json", false, Map.of());
        assertThat(passwordChanged.path("statusCode").asInt()).isEqualTo(200);
        assertPublicUser(JSON.readTree(body(passwordChanged)));
        String changedUserToken = login(email, replacementPassword);
        assertThat(request("GET", path, null, changedUserToken, "application/json", false, Map.of())
                .path("statusCode").asInt()).isEqualTo(200);
        assertThat(request("DELETE", path, null, token, "application/json", false, Map.of()).path("statusCode").asInt()).isEqualTo(204);
        assertThat(request("GET", path, null, token, "application/json", false, Map.of()).path("statusCode").asInt()).isEqualTo(404);
    }

    @Test
    void preflightWorksWithoutJwt() throws Exception {
        var result = request("OPTIONS", "/usuarios", null, null, "application/json", false,
                Map.of("Origin", "http://localhost:8100", "Access-Control-Request-Method", "GET",
                        "Access-Control-Request-Headers", "Authorization"));
        assertThat(result.path("statusCode").asInt()).isEqualTo(200);
        assertThat(result.toString().toLowerCase()).contains("access-control-allow-origin", "http://localhost:8100");
    }

    @Test
    void multipartUploadAndBinaryDownloadPreserveTheContract() throws Exception {
        String token = login();
        byte[] bytes = {0, 1, 2, (byte) 255, (byte) 128, 10, 13};
        var uploaded = upload(token, bytes);
        assertThat(uploaded.path("statusCode").asInt()).isEqualTo(200);
        var response = JSON.readTree(body(uploaded));
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.path("id").asLong()).isPositive();
        assertThat(response.path("nombre").asText()).isEqualTo("fixture.bin");
        assertThat(response.path("mensaje").asText()).isEqualTo("Archivo subido correctamente");
        assertThat(response.path("url").asText()).isEqualTo("/uploads/" + response.path("nombreGuardado").asText());
        assertThat(OBJECTS.get(response.path("nombreGuardado").asText()).asByteArray()).isEqualTo(bytes);
        var downloaded = request("GET", response.path("url").asText(), null, token, "application/json", false, Map.of());
        assertThat(downloaded.path("statusCode").asInt()).isEqualTo(200);
        assertThat(downloaded.path("isBase64Encoded").asBoolean()).isTrue();
        assertThat(body(downloaded)).isEqualTo(bytes);
        assertThat(request("GET", response.path("url").asText(), null, null, "application/json", false, Map.of())
                .path("statusCode").asInt()).isIn(401, 403);
    }

    @Test
    void emptyUploadAndMissingDownloadKeepTheirErrorStatuses() throws Exception {
        String token = login();
        var empty = upload(token, new byte[0]);
        assertThat(empty.path("statusCode").asInt()).isEqualTo(400);
        assertThat(new String(body(empty), StandardCharsets.UTF_8)).isEqualTo("El archivo está vacío");
        assertThat(request("GET", "/uploads/missing.bin", null, token, "application/json", false, Map.of())
                .path("statusCode").asInt()).isEqualTo(404);
    }

    @Test
    void oversizedUploadIsRejectedBeforeStorage() throws Exception {
        int before = OBJECTS.size();
        var result = upload(login(), new byte[1024 * 1024 + 1]);
        assertThat(result.path("statusCode").asInt()).isEqualTo(413);
        assertThat(OBJECTS).hasSize(before);
    }

    private static String login() throws Exception {
        return login("lambda-test@example.invalid", PASSWORD);
    }

    private static void assertPublicUser(JsonNode user) {
        assertThat(user.has("password")).isFalse();
        assertThat(user.size()).isEqualTo(3);
        assertThat(user.hasNonNull("id") && user.hasNonNull("nombre") && user.hasNonNull("email")).isTrue();
    }

    private static String login(String email, String password) throws Exception {
        var result = request("POST", "/auth/login", JSON.writeValueAsString(Map.of(
                "email", email, "password", password)), null, "application/json", false, Map.of());
        assertThat(result.path("statusCode").asInt()).isEqualTo(200);
        var json = JSON.readTree(body(result));
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.path("token").asText()).isNotBlank();
        return json.path("token").asText();
    }

    private static JsonNode upload(String token, byte[] bytes) throws Exception {
        String boundary = "LambdaTestBoundary";
        var multipart = new ByteArrayOutputStream();
        multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"fixture.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(bytes);
        multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return request("POST", "/upload", Base64.getEncoder().encodeToString(multipart.toByteArray()), token,
                "multipart/form-data; boundary=" + boundary, true, Map.of());
    }

    private static JsonNode request(String method, String path, String body, String token, String contentType,
                                    boolean base64, Map<String, String> extraHeaders) throws Exception {
        Map<String, String> headers = new HashMap<>(extraHeaders);
        headers.put("Host", "example.execute-api.us-east-1.amazonaws.com");
        headers.put("Content-Type", contentType);
        headers.put("X-Forwarded-Proto", "https");
        if (token != null) headers.put("Authorization", "Bearer " + token);
        Map<String, Object> event = new HashMap<>();
        event.put("resource", "/{proxy+}");
        event.put("path", path);
        event.put("httpMethod", method);
        event.put("headers", headers);
        // REST API payload 1.0 includes both header maps; the adapter uses multi-value headers.
        Map<String, Object> multiValueHeaders = new HashMap<>();
        headers.forEach((name, value) -> multiValueHeaders.put(name, java.util.List.of(value)));
        event.put("multiValueHeaders", multiValueHeaders);
        event.put("body", body);
        event.put("isBase64Encoded", base64);
        event.put("requestContext", Map.of("stage", "test", "requestId", "test-request", "httpMethod", method,
                "resourcePath", "/{proxy+}", "path", "/test" + path,
                "identity", Map.of("sourceIp", "127.0.0.1", "userAgent", "JUnit")));
        var output = new ByteArrayOutputStream();
        handler.handleRequest(new ByteArrayInputStream(JSON.writeValueAsBytes(event)), output, context);
        return JSON.readTree(output.toByteArray());
    }

    private static byte[] body(JsonNode response) {
        String body = response.path("body").asText("");
        return response.path("isBase64Encoded").asBoolean()
                ? Base64.getDecoder().decode(body) : body.getBytes(StandardCharsets.UTF_8);
    }
}
