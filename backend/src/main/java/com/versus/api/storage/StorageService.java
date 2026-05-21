package com.versus.api.storage;

import java.io.InputStream;

public interface StorageService {
    StoredObject put(String objectKey, InputStream content, long sizeBytes, String contentType);
    void delete(String objectKey);
}
