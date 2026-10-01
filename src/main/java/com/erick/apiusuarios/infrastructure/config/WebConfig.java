package com.erick.apiusuarios.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("!lambda")
public class WebConfig implements WebMvcConfigurer {

    private final String uploadLocation;

    public WebConfig(@Value("${app.storage.local-directory:uploads}") String directory) {
        String uri = Path.of(directory).toAbsolutePath().normalize().toUri().toString();
        this.uploadLocation = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(
            ResourceHandlerRegistry registry
    ) {

        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
    }
}
