package com.versus.api.cards;

import com.versus.api.cards.dto.CardImportRequest;
import com.versus.api.cards.dto.CardImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Cards", description = "Card management (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardImportService cardImportService;

    @Operation(summary = "Import cards from normalized JSON",
            responses = {
                @ApiResponse(responseCode = "200", description = "Import completed"),
                @ApiResponse(responseCode = "403", description = "Admin role required")
            })
    @PostMapping("/import")
    public CardImportResponse importCards(@RequestBody(required = false) CardImportRequest request) {
        if (request == null) {
            request = new CardImportRequest(null, false);
        }
        return cardImportService.importFrom(request);
    }
}
