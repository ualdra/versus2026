package com.versus.api.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("R2StorageService")
class R2StorageServiceTest {

    @Test
    @DisplayName("publicUrl concatena la base CDN sin duplicar barras")
    void publicUrlBuildsCdnUrlWithoutDuplicateSlashes() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setPublicBaseUrl("https://cdn.example.com/media/");

        R2StorageService service = new R2StorageService(properties, null);

        assertThat(service.publicUrl("image/key.png")).isEqualTo("https://cdn.example.com/media/image/key.png");
    }
}
