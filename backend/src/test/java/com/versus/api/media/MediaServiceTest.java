package com.versus.api.media;

import com.versus.api.common.exception.ApiException;
import com.versus.api.common.exception.ErrorCode;
import com.versus.api.media.domain.MediaAsset;
import com.versus.api.media.dto.MediaAssetResponse;
import com.versus.api.media.repo.MediaAssetRepository;
import com.versus.api.storage.StorageProperties;
import com.versus.api.storage.StorageService;
import com.versus.api.storage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MediaService")
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock MediaAssetRepository assets;
    @Mock StorageService storage;

    StorageProperties properties;
    MediaService mediaService;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setMaxFileSizeBytes(1024);
        properties.setMaxAvatarSizeBytes(512);
        mediaService = new MediaService(assets, storage, properties);
    }

    @Test
    @DisplayName("upload guarda el objeto y persiste metadatos")
    void uploadStoresObjectAndMetadata() {
        UUID ownerId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] {1, 2, 3});
        when(storage.put(anyString(), any(), eq(3L), eq("image/png")))
                .thenReturn(new StoredObject("image/key.png", "https://cdn.test/image/key.png"));
        when(assets.save(any(MediaAsset.class))).thenAnswer(inv -> persisted(inv.getArgument(0)));

        MediaAssetResponse response = mediaService.upload(ownerId, file, MediaKind.IMAGE, MediaVisibility.PUBLIC);

        assertThat(response.kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(response.filename()).isEqualTo("photo.png");
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.url()).isEqualTo("https://cdn.test/image/key.png");
        verify(storage).put(contains(ownerId.toString()), any(), eq(3L), eq("image/png"));
    }

    @Test
    @DisplayName("upload rechaza archivos vacios")
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertValidation(() -> mediaService.upload(UUID.randomUUID(), file, MediaKind.IMAGE, MediaVisibility.PUBLIC));
        verifyNoInteractions(storage, assets);
    }

    @Test
    @DisplayName("upload rechaza MIME no permitido")
    void uploadRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "run.exe", "application/x-msdownload", new byte[] {1});

        assertValidation(() -> mediaService.upload(UUID.randomUUID(), file, MediaKind.OTHER, MediaVisibility.PRIVATE));
        verifyNoInteractions(storage, assets);
    }

    @Test
    @DisplayName("uploadAvatar exige imagen y limite propio")
    void uploadAvatarRequiresImageAndAvatarLimit() {
        MockMultipartFile document = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1});
        MockMultipartFile tooLarge = new MockMultipartFile("file", "avatar.png", "image/png", new byte[513]);

        assertValidation(() -> mediaService.uploadAvatar(UUID.randomUUID(), document));
        assertValidation(() -> mediaService.uploadAvatar(UUID.randomUUID(), tooLarge));
        verifyNoInteractions(storage, assets);
    }

    @Test
    @DisplayName("delete impide borrar assets ajenos si no es admin")
    void deleteRejectsForeignAssetForNonAdmin() {
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        MediaAsset asset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .objectKey("image/key.png")
                .build();
        when(assets.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> mediaService.delete(requesterId, false, asset.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(storage, never()).delete(anyString());
        verify(assets, never()).delete(any());
    }

    @Test
    @DisplayName("delete permite a ADMIN borrar assets ajenos")
    void deleteAllowsAdmin() {
        MediaAsset asset = MediaAsset.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .objectKey("image/key.png")
                .build();
        when(assets.findById(asset.getId())).thenReturn(Optional.of(asset));

        mediaService.delete(UUID.randomUUID(), true, asset.getId());

        verify(storage).delete("image/key.png");
        verify(assets).delete(asset);
    }

    private MediaAsset persisted(MediaAsset asset) {
        asset.setId(UUID.randomUUID());
        asset.setCreatedAt(Instant.now());
        return asset;
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
