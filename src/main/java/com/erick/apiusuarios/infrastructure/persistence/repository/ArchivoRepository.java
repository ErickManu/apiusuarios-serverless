package com.erick.apiusuarios.infrastructure.persistence.repository;

import com.erick.apiusuarios.domain.model.Archivo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchivoRepository
        extends JpaRepository<Archivo, Long> {
}