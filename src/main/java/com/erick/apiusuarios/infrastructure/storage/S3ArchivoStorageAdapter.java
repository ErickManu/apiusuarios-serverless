package com.erick.apiusuarios.infrastructure.storage;

import com.erick.apiusuarios.domain.port.ArchivoStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;

@Component
@Profile("lambda")
public class S3ArchivoStorageAdapter implements ArchivoStoragePort {
    private final S3Client client;
    private final String bucket;

    public S3ArchivoStorageAdapter(S3Client client, @Value("${app.storage.s3.bucket}") String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3_BUCKET es obligatorio en Lambda");
        }
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void guardar(String nombre, InputStream contenido, long longitud, String contentType) throws IOException {
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(nombre)
                            .contentType(contentType == null ? "application/octet-stream" : contentType).build(),
                    RequestBody.fromInputStream(contenido, longitud));
        } catch (SdkException e) {
            throw new IOException("Error al guardar el archivo en S3", e);
        }
    }

    @Override
    public Contenido leer(String nombre) throws IOException {
        try {
            var response = client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(nombre).build());
            return new Contenido(response.asByteArray(), response.response().contentType());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new NoSuchFileException(nombre);
            }
            throw new IOException("Error al leer el archivo en S3", e);
        } catch (SdkException e) {
            throw new IOException("Error al leer el archivo en S3", e);
        }
    }
}
