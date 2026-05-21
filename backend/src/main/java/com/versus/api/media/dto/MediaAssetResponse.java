package com.versus.api.media.dto;

import com.versus.api.media.MediaKind;
import com.versus.api.media.MediaVisibility;

import java.time.Instant;

public record MediaAssetResponse(
        String id,
        MediaKind kind,
        String filename,
        String contentType,
        long sizeBytes,
        MediaVisibility visibility,
        String url,
        Instant createdAt
) { }
