package com.erick.apiusuarios.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ArchivoStorageAdapterTest {
    private final S3Client client = mock(S3Client.class);
    private final S3ArchivoStorageAdapter storage = new S3ArchivoStorageAdapter(client, "test-bucket");

    @Test
    void uploadPreservesTheKeyBytesAndContentType() throws Exception {
        byte[] bytes = {0, (byte) 255, 10};
        storage.guardar("uuid.bin", new ByteArrayInputStream(bytes), bytes.length, "application/octet-stream");
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo("uuid.bin");
        assertThat(request.getValue().contentType()).isEqualTo("application/octet-stream");
        try (var stream = body.getValue().contentStreamProvider().newStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void downloadPreservesTheBytesAndMetadata() throws Exception {
        byte[] bytes = {4, 5, (byte) 200};
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentType("image/png").build(), bytes));
        var content = storage.leer("uuid.png");
        assertThat(content.bytes()).isEqualTo(bytes);
        assertThat(content.contentType()).isEqualTo("image/png");
        verify(client).getObjectAsBytes(GetObjectRequest.builder().bucket("test-bucket").key("uuid.png").build());
    }

    @Test
    void missingObjectIsNotFound() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build());
        assertThatThrownBy(() -> storage.leer("missing.bin")).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void accessDeniedIsNotReportedAsMissingObject() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());
        assertThatThrownBy(() -> storage.leer("private.bin"))
                .isInstanceOf(IOException.class).isNotInstanceOf(NoSuchFileException.class);
    }

    @Test
    void uploadFailureBecomesAnIoExceptionForTheExistingController() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("Unavailable"));
        assertThatThrownBy(() -> storage.guardar("uuid.bin", new ByteArrayInputStream(new byte[0]), 0, null))
                .isInstanceOf(IOException.class);
    }
}
