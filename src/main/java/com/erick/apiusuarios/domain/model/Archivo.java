package com.erick.apiusuarios.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "archivos")
public class Archivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreOriginal;

    private String nombreGuardado;

    private String url;

    public Archivo() {
    }

    public Archivo(
            String nombreOriginal,
            String nombreGuardado,
            String url
    ) {
        this.nombreOriginal = nombreOriginal;
        this.nombreGuardado = nombreGuardado;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public String getNombreGuardado() {
        return nombreGuardado;
    }

    public String getUrl() {
        return url;
    }
}