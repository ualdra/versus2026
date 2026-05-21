package com.versus.api.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

@Service
@ConditionalOnProperty(prefix = "versus.storage", name = "provider", havingValue = "r2")
public class R2StorageService implements StorageService {

    private final StorageProperties properties;
    private final S3Client s3;

    public R2StorageService(StorageProperties properties) {
        this(properties, createClient(properties.getR2()));
    }

    R2StorageService(StorageProperties properties, S3Client s3) {
        this.properties = properties;
        this.s3 = s3;
    }

    private static S3Client createClient(StorageProperties.R2 r2) {
        return S3Client.builder()
                .endpointOverride(URI.create(required(r2.getEndpoint(), "R2 endpoint is required")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        required(r2.getAccessKeyId(), "R2 access key is required"),
                        required(r2.getSecretAccessKey(), "R2 secret key is required"))))
                .region(Region.of("auto"))
                .forcePathStyle(true)
                .build();
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long sizeBytes, String contentType) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentLength(sizeBytes)
                .contentType(contentType)
                .build();
        s3.putObject(req, RequestBody.fromInputStream(content, sizeBytes));
        return new StoredObject(objectKey, publicUrl(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build());
    }

    String publicUrl(String objectKey) {
        String base = properties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        return base.replaceAll("/+$", "") + "/" + objectKey;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
