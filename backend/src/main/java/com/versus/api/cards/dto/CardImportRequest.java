package com.versus.api.cards.dto;

public record CardImportRequest(
        String path,
        boolean purgeFirst
) {
}
