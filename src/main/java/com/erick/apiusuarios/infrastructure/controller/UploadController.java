package com.erick.apiusuarios.infrastructure.controller;

import com.erick.apiusuarios.domain.model.Archivo;
import com.erick.apiusuarios.domain.port.ArchivoStoragePort;
import com.erick.apiusuarios.infrastructure.persistence.repository.ArchivoRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class UploadController {

    private final ArchivoRepository archivoRepository;
    private final ArchivoStoragePort storage;
    private final long maxFileSize;
    private final long maxRequestSize;

    public UploadController(ArchivoRepository archivoRepository, ArchivoStoragePort storage,
                            @Value("${spring.servlet.multipart.max-file-size:1MB}") String maxFileSize,
                            @Value("${spring.servlet.multipart.max-request-size:10MB}") String maxRequestSize) {
        this.archivoRepository = archivoRepository;
        this.storage = storage;
        this.maxFileSize = DataSize.parse(maxFileSize).toBytes();
        this.maxRequestSize = DataSize.parse(maxRequestSize).toBytes();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> subirArchivo(
            @RequestParam("archivo") MultipartFile archivo,
            HttpServletRequest request
    ) {

        try {

            // The Lambda servlet adapter does not enforce embedded Tomcat's multipart limits.
            if (maxFileSize >= 0 && archivo.getSize() > maxFileSize) {
                throw new MaxUploadSizeExceededException(maxFileSize);
            }
            if (maxRequestSize >= 0 && request.getContentLengthLong() > maxRequestSize) {
                throw new MaxUploadSizeExceededException(maxRequestSize);
            }

            if (archivo.isEmpty()) {
                return ResponseEntity
                        .badRequest()
                        .body("El archivo está vacío");
            }

            String nombreOriginal = archivo.getOriginalFilename();

            String extension = "";

            if (nombreOriginal != null &&
                    nombreOriginal.contains(".")) {

                extension = nombreOriginal.substring(
                        nombreOriginal.lastIndexOf(".")
                );
            }

            // Crear nombre único
            String nombreGuardado =
                    UUID.randomUUID().toString() + extension;

            try (var contenido = archivo.getInputStream()) {
                storage.guardar(nombreGuardado, contenido, archivo.getSize(), archivo.getContentType());
            }

            // URL que guardaremos en PostgreSQL
            String url =
                    "/uploads/" + nombreGuardado;

            // Guardar referencia en BD
            Archivo archivoBD =
                    new Archivo(
                            nombreOriginal,
                            nombreGuardado,
                            url
                    );

            archivoRepository.save(archivoBD);

            // Respuesta para Ionic
            Map<String, Object> respuesta =
                    new HashMap<>();

            respuesta.put(
                    "id",
                    archivoBD.getId()
            );

            respuesta.put(
                    "nombre",
                    nombreOriginal
            );

            respuesta.put(
                    "nombreGuardado",
                    nombreGuardado
            );

            respuesta.put(
                    "url",
                    url
            );

            respuesta.put(
                    "mensaje",
                    "Archivo subido correctamente"
            );

            return ResponseEntity.ok(respuesta);

        } catch (IOException e) {

            return ResponseEntity
                    .internalServerError()
                    .body("Error al guardar el archivo");
        }
    }
}
