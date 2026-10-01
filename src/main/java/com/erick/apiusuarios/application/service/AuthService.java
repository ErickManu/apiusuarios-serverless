package com.erick.apiusuarios.application.service;

import com.erick.apiusuarios.domain.model.Usuario;
import com.erick.apiusuarios.domain.port.PasswordEncoderPort;
import com.erick.apiusuarios.domain.port.UsuarioRepositoryPort;
import com.erick.apiusuarios.infrastructure.security.JwtService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepositoryPort repository;
    private final PasswordEncoderPort passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepositoryPort repository,
            PasswordEncoderPort passwordEncoder,
            JwtService jwtService) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String login(String email, String password) {

        Usuario usuario = repository.buscarPorEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(
                password,
                usuario.getPassword())) {

            throw new RuntimeException("Contraseña incorrecta");
        }

        return jwtService.generarToken(usuario.getEmail());
    }
}