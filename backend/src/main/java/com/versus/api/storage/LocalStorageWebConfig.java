package com.versus.api.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "versus.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageWebConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String publicPath = properties.getPublicPath();
        if (publicPath == null || publicPath.isBlank()) {
            publicPath = "/media-files/";
        }
        if (!publicPath.startsWith("/")) {
            publicPath = "/" + publicPath;
        }
        if (!publicPath.endsWith("/")) {
            publicPath = publicPath + "/";
        }

        Path root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
        registry.addResourceHandler(publicPath + "**")
                .addResourceLocations(root.toUri().toString());
    }
}
