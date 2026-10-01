package com.erick.apiusuarios.infrastructure.controller;

import com.erick.apiusuarios.domain.port.ArchivoStoragePort;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

@RestController
@Profile("lambda")
public class ArchivoController {
    private final ArchivoStoragePort storage;

    public ArchivoController(ArchivoStoragePort storage) {
        this.storage = storage;
    }

    @GetMapping("/uploads/{nombreGuardado}")
    public ResponseEntity<?> descargar(@PathVariable String nombreGuardado) {
        try {
            var contenido = storage.leer(nombreGuardado);
            MediaType type = MediaType.APPLICATION_OCTET_STREAM;
            if (contenido.contentType() != null) {
                try {
                    type = MediaType.parseMediaType(contenido.contentType());
                } catch (IllegalArgumentException ignored) {
                    // Invalid object metadata must not prevent the download.
                }
            }
            return ResponseEntity.ok().contentType(type).contentLength(contenido.bytes().length)
                    .body(contenido.bytes());
        } catch (NoSuchFileException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al leer el archivo");
        }
    }
}
