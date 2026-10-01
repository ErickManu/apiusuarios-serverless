package com.erick.apiusuarios.infrastructure.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/datos")
public class DatosController {

    @GetMapping("/perfil")
    public ResponseEntity<Map<String, Object>> obtenerPerfil() {

        return ResponseEntity.ok(
                Map.of(
                        "nombre", "Usuario autenticado",
                        "tipo", "Administrador",
                        "activo", true
                )
        );
    }


    @GetMapping("/configuracion")
    public ResponseEntity<Map<String, Object>> obtenerConfiguracion() {

        return ResponseEntity.ok(
                Map.of(
                        "tema", "Sistema",
                        "idioma", "Español",
                        "notificaciones", true
                )
        );
    }


    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> obtenerEstado() {

        return ResponseEntity.ok(
                Map.of(
                        "api", "Activa",
                        "baseDatos", "Conectada",
                        "mensaje", "Sistema funcionando correctamente"
                )
        );
    }
}