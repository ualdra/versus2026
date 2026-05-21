package com.versus.api.storage;

import com.versus.api.common.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "versus.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;
    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.properties = properties;
        this.root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long sizeBytes, String contentType) {
        try {
            Path destination = root.resolve(objectKey).normalize();
            if (!destination.startsWith(root)) {
                throw ApiException.validation("Invalid object key");
            }
            Files.createDirectories(destination.getParent());
            Files.copy(content, destination);
            return new StoredObject(objectKey, publicUrl(objectKey));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store file locally", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Path destination = root.resolve(objectKey).normalize();
            if (destination.startsWith(root)) {
                Files.deleteIfExists(destination);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not delete local file", ex);
        }
    }

    private String publicUrl(String objectKey) {
        String base = properties.getPublicBaseUrl();
        String path = properties.getPublicPath();
        String normalizedPath = (path == null || path.isBlank()) ? "/media-files/" : path;
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }
        String relativeUrl = normalizedPath + objectKey;
        return (base == null || base.isBlank())
                ? relativeUrl
                : base.replaceAll("/+$", "") + relativeUrl;
    }
}
