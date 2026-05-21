package com.versus.api.storage;

public record StoredObject(
        String objectKey,
        String publicUrl
) { }
