package com.versus.api.cards.dto;

import java.util.List;

public record CardImportResponse(
        int totalRead,
        int inserted,
        int skippedDuplicates,
        int skippedFiltered,
        List<String> errors
) {
}
