package com.erick.apiusuarios.domain.port;

import com.erick.apiusuarios.domain.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositoryPort {

    Usuario guardar(Usuario usuario);

    List<Usuario> listar();

    Optional<Usuario> buscarPorId(Long id);

    Optional<Usuario> buscarPorEmail(String email);

    Usuario actualizar(Usuario usuario);

    void eliminar(Long id);
}