package com.erick.apiusuarios.infrastructure.controller;

import com.erick.apiusuarios.domain.model.Usuario;

/** Public user representation. Credentials belong only to the internal model. */
public record UsuarioResponse(Long id, String nombre, String email) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail());
    }
}
