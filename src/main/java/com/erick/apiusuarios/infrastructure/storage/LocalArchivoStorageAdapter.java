package com.erick.apiusuarios.infrastructure.storage;

import com.erick.apiusuarios.domain.port.ArchivoStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@Profile("!lambda")
public class LocalArchivoStorageAdapter implements ArchivoStoragePort {
    private final Path directory;

    public LocalArchivoStorageAdapter(@Value("${app.storage.local-directory:uploads}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void guardar(String nombre, InputStream contenido, long longitud, String contentType) throws IOException {
        Path destination = resolve(nombre);
        Files.createDirectories(directory);
        Files.copy(contenido, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Contenido leer(String nombre) throws IOException {
        Path file = resolve(nombre);
        return new Contenido(Files.readAllBytes(file), Files.probeContentType(file));
    }

    private Path resolve(String nombre) throws IOException {
        Path file = directory.resolve(nombre).normalize();
        if (!directory.equals(file.getParent())) {
            throw new IOException("Nombre de archivo no válido");
        }
        return file;
    }
}
