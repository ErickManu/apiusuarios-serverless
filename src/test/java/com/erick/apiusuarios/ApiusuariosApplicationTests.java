package com.erick.apiusuarios;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.ApplicationContext;
import com.erick.apiusuarios.domain.port.ArchivoStoragePort;
import com.erick.apiusuarios.infrastructure.storage.LocalArchivoStorageAdapter;
import com.erick.apiusuarios.infrastructure.security.JwtService;
import software.amazon.awssdk.services.s3.S3Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.erick.apiusuarios.domain.port.UsuarioRepositoryPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ApiusuariosApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiusuariosApplicationTests {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UsuarioRepositoryPort usuarioRepository;

	@Test
	void contextLoads() {
		assertThat(context.getBean(ArchivoStoragePort.class)).isInstanceOf(LocalArchivoStorageAdapter.class);
		assertThat(context.getBeansOfType(S3Client.class)).isEmpty();
	}

    @Test
    void userResponsesNeverExposePasswordsAndLoginStillWorks() throws Exception {
        var json = new ObjectMapper();
        String email = UUID.randomUUID() + "@example.invalid";
        String password = UUID.randomUUID().toString();
        String authorization = "Bearer " + jwtService.generarToken("local-test@example.invalid");
        var created = mvc.perform(post("/usuarios").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("nombre", "Inicial", "email", email, "password", password))))
                .andExpect(status().isCreated()).andReturn();
        var user = json.readTree(created.getResponse().getContentAsByteArray());
        assertPublicUser(user);
        long id = user.path("id").asLong();
        String path = "/usuarios/" + id;

        String storedHash = usuarioRepository.buscarPorId(id).orElseThrow().getPassword();
        assertThat(storedHash).isNotEqualTo(password);
        assertThat(new BCryptPasswordEncoder().matches(password, storedHash)).isTrue();
        assertLocalLogin(json, email, password);

        var found = mvc.perform(get(path).header("Authorization", authorization))
                .andExpect(status().isOk()).andReturn();
        assertPublicUser(json.readTree(found.getResponse().getContentAsByteArray()));
        var listed = mvc.perform(get("/usuarios").header("Authorization", authorization))
                .andExpect(status().isOk()).andReturn();
        var users = json.readTree(listed.getResponse().getContentAsByteArray());
        assertThat(users.isArray()).isTrue();
        assertThat(users.size()).isPositive();
        users.forEach(ApiusuariosApplicationTests::assertPublicUser);

        var unchangedPassword = mvc.perform(put(path).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("nombre", "Editado", "email", email))))
                .andExpect(status().isOk()).andReturn();
        assertPublicUser(json.readTree(unchangedPassword.getResponse().getContentAsByteArray()));
        assertThat(usuarioRepository.buscarPorId(id).orElseThrow().getPassword()).isEqualTo(storedHash);

        String replacementPassword = UUID.randomUUID().toString();
        var updated = mvc.perform(put(path).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("nombre", "Actualizado", "email", email,
                                "password", replacementPassword))))
                .andExpect(status().isOk()).andReturn();
        assertPublicUser(json.readTree(updated.getResponse().getContentAsByteArray()));
        String updatedHash = usuarioRepository.buscarPorId(id).orElseThrow().getPassword();
        assertThat(updatedHash).isNotEqualTo(storedHash);
        assertThat(new BCryptPasswordEncoder().matches(replacementPassword, updatedHash)).isTrue();
        assertLocalLogin(json, email, replacementPassword);

        mvc.perform(delete(path).header("Authorization", authorization)).andExpect(status().isNoContent());
        mvc.perform(get(path).header("Authorization", authorization)).andExpect(status().isNotFound());
    }

    private static void assertPublicUser(JsonNode user) {
        assertThat(user.has("password")).isFalse();
        assertThat(user.size()).isEqualTo(3);
        assertThat(user.hasNonNull("id") && user.hasNonNull("nombre") && user.hasNonNull("email")).isTrue();
    }

    private void assertLocalLogin(ObjectMapper json, String email, String password) throws Exception {
        var response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        var login = json.readTree(response.getResponse().getContentAsByteArray());
        assertThat(login.size()).isEqualTo(1);
        assertThat(jwtService.tokenValido(login.path("token").asText())).isTrue();
    }

    @Test
    void localUploadAndDownloadKeepTheExistingContract() throws Exception {
        byte[] bytes = new byte[] {0, 1, 2, (byte) 255, 10, 13};
        String authorization = "Bearer " + jwtService.generarToken("local-test@example.invalid");
        var result = mvc.perform(multipart("/upload")
                        .file(new MockMultipartFile("archivo", "local.bin", "application/octet-stream", bytes))
                        .header("Authorization", authorization))
                .andExpect(status().isOk()).andReturn();
        var body = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.path("nombre").asText()).isEqualTo("local.bin");
        assertThat(body.path("mensaje").asText()).isEqualTo("Archivo subido correctamente");
        Path saved = Path.of("target/test-local-uploads", body.path("nombreGuardado").asText());
        try {
            assertThat(Files.readAllBytes(saved)).isEqualTo(bytes);
            var download = mvc.perform(get(body.path("url").asText()).header("Authorization", authorization))
                    .andExpect(status().isOk()).andReturn();
            assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        } finally {
            Files.deleteIfExists(saved);
        }
    }

}
