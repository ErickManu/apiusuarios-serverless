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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ApiusuariosApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiusuariosApplicationTests {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;

	@Test
	void contextLoads() {
		assertThat(context.getBean(ArchivoStoragePort.class)).isInstanceOf(LocalArchivoStorageAdapter.class);
		assertThat(context.getBeansOfType(S3Client.class)).isEmpty();
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
