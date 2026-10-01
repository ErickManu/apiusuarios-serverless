package com.erick.apiusuarios.application.service;

import com.erick.apiusuarios.domain.model.Usuario;
import com.erick.apiusuarios.domain.port.PasswordEncoderPort;
import com.erick.apiusuarios.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepositoryPort repository;
    private final PasswordEncoderPort passwordEncoder;

    public UsuarioService(
            UsuarioRepositoryPort repository,
            PasswordEncoderPort passwordEncoder) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario crear(Usuario usuario) {

        if (repository.buscarPorEmail(usuario.getEmail()).isPresent()) {
            throw new RuntimeException("El email ya está registrado");
        }

        usuario.setPassword(
                passwordEncoder.encode(usuario.getPassword())
        );

        return repository.guardar(usuario);
    }

    public List<Usuario> listar() {
        return repository.listar();
    }

    public Optional<Usuario> buscarPorId(Long id) {
        return repository.buscarPorId(id);
    }

    public Optional<Usuario> actualizar(Long id, Usuario datos) {

        return repository.buscarPorId(id).map(usuario -> {

            usuario.setNombre(datos.getNombre());
            usuario.setEmail(datos.getEmail());

            if (datos.getPassword() != null &&
                    !datos.getPassword().isBlank()) {

                usuario.setPassword(
                        passwordEncoder.encode(datos.getPassword())
                );
            }

            return repository.actualizar(usuario);
        });
    }

    public boolean eliminar(Long id) {

        if (repository.buscarPorId(id).isEmpty()) {
            return false;
        }

        repository.eliminar(id);
        return true;
    }
}