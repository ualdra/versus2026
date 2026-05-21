package com.versus.api.media;

import com.versus.api.common.exception.ApiException;
import com.versus.api.media.domain.MediaAsset;
import com.versus.api.media.dto.MediaAssetResponse;
import com.versus.api.media.repo.MediaAssetRepository;
import com.versus.api.storage.StorageProperties;
import com.versus.api.storage.StorageService;
import com.versus.api.storage.StoredObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final MediaAssetRepository assets;
    private final StorageService storage;
    private final StorageProperties properties;

    @Transactional
    public MediaAssetResponse upload(UUID ownerId, MultipartFile file, MediaKind kind, MediaVisibility visibility) {
        validate(file, properties.getMaxFileSizeBytes(), false);
        return toResponse(store(ownerId, file, kind, visibility));
    }

    @Transactional
    public MediaAssetResponse uploadAvatar(UUID ownerId, MultipartFile file) {
        validate(file, properties.getMaxAvatarSizeBytes(), true);
        return toResponse(store(ownerId, file, MediaKind.IMAGE, MediaVisibility.PUBLIC));
    }

    @Transactional(readOnly = true)
    public MediaAssetResponse get(UUID requesterId, UUID assetId) {
        MediaAsset asset = assets.findById(assetId)
                .orElseThrow(() -> ApiException.notFound("Media asset not found"));
        if (asset.getVisibility() == MediaVisibility.PRIVATE && !asset.getOwnerId().equals(requesterId)) {
            throw ApiException.forbidden("You cannot access this media asset");
        }
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> listMine(UUID ownerId) {
        return assets.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID requesterId, boolean admin, UUID assetId) {
        MediaAsset asset = assets.findById(assetId)
                .orElseThrow(() -> ApiException.notFound("Media asset not found"));
        if (!admin && !asset.getOwnerId().equals(requesterId)) {
            throw ApiException.forbidden("You cannot delete this media asset");
        }
        storage.delete(asset.getObjectKey());
        assets.delete(asset);
    }

    private MediaAsset store(UUID ownerId, MultipartFile file, MediaKind kind, MediaVisibility visibility) {
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String contentType = normalizedContentType(file);
        String objectKey = objectKey(ownerId, kind, originalFilename);
        try {
            StoredObject stored = storage.put(objectKey, file.getInputStream(), file.getSize(), contentType);
            MediaAsset asset = MediaAsset.builder()
                    .ownerId(ownerId)
                    .kind(kind == null ? MediaKind.OTHER : kind)
                    .originalFilename(originalFilename)
                    .objectKey(stored.objectKey())
                    .contentType(contentType)
                    .sizeBytes(file.getSize())
                    .visibility(visibility == null ? MediaVisibility.PRIVATE : visibility)
                    .publicUrl(stored.publicUrl())
                    .build();
            return assets.save(asset);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store media asset", ex);
        }
    }

    private void validate(MultipartFile file, long maxSizeBytes, boolean imageOnly) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("File is required");
        }
        if (file.getSize() > maxSizeBytes) {
            throw ApiException.validation("File exceeds maximum size");
        }
        String contentType = normalizedContentType(file);
        if (!properties.getAllowedContentTypes().contains(contentType)) {
            throw ApiException.validation("Unsupported content type");
        }
        if (imageOnly && !IMAGE_TYPES.contains(contentType)) {
            throw ApiException.validation("Avatar must be an image");
        }
    }

    private String normalizedContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw ApiException.validation("Content type is required");
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private String objectKey(UUID ownerId, MediaKind kind, String filename) {
        LocalDate today = LocalDate.now();
        String extension = extension(filename);
        String typePath = (kind == null ? MediaKind.OTHER : kind).name().toLowerCase(Locale.ROOT);
        return "%s/%d/%02d/%s/%s%s".formatted(
                typePath,
                today.getYear(),
                today.getMonthValue(),
                ownerId,
                UUID.randomUUID(),
                extension);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String normalizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return base.isBlank() ? "upload" : base;
    }

    private MediaAssetResponse toResponse(MediaAsset asset) {
        return new MediaAssetResponse(
                asset.getId().toString(),
                asset.getKind(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getVisibility(),
                asset.getPublicUrl(),
                asset.getCreatedAt());
    }
}
