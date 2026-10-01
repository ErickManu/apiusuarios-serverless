package com.erick.apiusuarios.domain.port;

public interface PasswordEncoderPort {

    String encode(String password);

    boolean matches(String password, String encodedPassword);
}