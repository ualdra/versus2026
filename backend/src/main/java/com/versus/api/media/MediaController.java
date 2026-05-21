package com.versus.api.media;

import com.versus.api.common.dto.ErrorResponse;
import com.versus.api.media.dto.MediaAssetResponse;
import com.versus.api.media.dto.MediaUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Media", description = "Multimedia and static asset management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "Upload a media asset",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Asset uploaded"),
                    @ApiResponse(responseCode = "400", description = "Invalid file",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse upload(@AuthenticationPrincipal UUID userId,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "kind", required = false) MediaKind kind,
                                      @RequestParam(value = "visibility", required = false) MediaVisibility visibility) {
        return new MediaUploadResponse(mediaService.upload(userId, file, kind, visibility));
    }

    @Operation(summary = "Get media asset metadata")
    @GetMapping("/{id}")
    public MediaAssetResponse get(@AuthenticationPrincipal UUID userId,
                                  @PathVariable("id") UUID id) {
        return mediaService.get(userId, id);
    }

    @Operation(summary = "List authenticated user's media assets")
    @GetMapping("/me")
    public List<MediaAssetResponse> mine(@AuthenticationPrincipal UUID userId) {
        return mediaService.listMine(userId);
    }

    @Operation(summary = "Delete a media asset")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId,
                       Authentication authentication,
                       @PathVariable("id") UUID id) {
        mediaService.delete(userId, isAdmin(authentication), id);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
