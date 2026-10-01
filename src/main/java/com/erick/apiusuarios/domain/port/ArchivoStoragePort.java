package com.erick.apiusuarios.domain.port;

import java.io.IOException;
import java.io.InputStream;

public interface ArchivoStoragePort {
    void guardar(String nombreGuardado, InputStream contenido, long longitud, String contentType) throws IOException;

    Contenido leer(String nombreGuardado) throws IOException;

    record Contenido(byte[] bytes, String contentType) { }
}
