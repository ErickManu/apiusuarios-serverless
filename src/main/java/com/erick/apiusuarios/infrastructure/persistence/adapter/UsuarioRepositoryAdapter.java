package com.erick.apiusuarios.infrastructure.persistence.adapter;

import com.erick.apiusuarios.domain.model.Usuario;
import com.erick.apiusuarios.domain.port.UsuarioRepositoryPort;
import com.erick.apiusuarios.infrastructure.persistence.entity.UsuarioEntity;
import com.erick.apiusuarios.infrastructure.persistence.repository.UsuarioJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {

    private final UsuarioJpaRepository repository;

    public UsuarioRepositoryAdapter(UsuarioJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        UsuarioEntity entity = convertirAEntity(usuario);
        UsuarioEntity guardado = repository.save(entity);

        return convertirADominio(guardado);
    }

    @Override
    public List<Usuario> listar() {
        return repository.findAll()
                .stream()
                .map(this::convertirADominio)
                .toList();
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id)
                .map(this::convertirADominio);
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(email)
                .map(this::convertirADominio);
    }

    @Override
    public Usuario actualizar(Usuario usuario) {
        UsuarioEntity entity = convertirAEntity(usuario);
        UsuarioEntity actualizado = repository.save(entity);

        return convertirADominio(actualizado);
    }

    @Override
    public void eliminar(Long id) {
        repository.deleteById(id);
    }

    private UsuarioEntity convertirAEntity(Usuario usuario) {
        return new UsuarioEntity(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getPassword()
        );
    }

    private Usuario convertirADominio(UsuarioEntity entity) {
        return new Usuario(
                entity.getId(),
                entity.getNombre(),
                entity.getEmail(),
                entity.getPassword()
        );
    }
}