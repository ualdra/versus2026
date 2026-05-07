package com.versus.api.scraping;

import com.versus.api.scraping.dto.SpiderResponse;
import com.versus.api.scraping.dto.SpiderRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin — Spiders", description = "Spider management (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/spiders")
@RequiredArgsConstructor
public class SpiderController {

    private final SpiderService spiderService;

    @Operation(summary = "List all spiders with their last run status",
            responses = @ApiResponse(responseCode = "200", description = "Spider list returned"))
    @GetMapping
    public List<SpiderResponse> list() {
        return spiderService.listSpiders();
    }

    @Operation(summary = "Trigger a spider run by name",
            responses = {
                @ApiResponse(responseCode = "202", description = "Run started"),
                @ApiResponse(responseCode = "404", description = "Spider not found"),
                @ApiResponse(responseCode = "409", description = "Spider already running")
            })
    @PostMapping("/{name}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SpiderRunResponse run(@PathVariable String name) {
        return spiderService.triggerRun(name);
    }

    @Operation(summary = "Get run history for a spider",
            responses = {
                @ApiResponse(responseCode = "200", description = "Run history returned"),
                @ApiResponse(responseCode = "404", description = "Spider not found")
            })
    @GetMapping("/{name}/runs")
    public List<SpiderRunResponse> runs(@PathVariable String name) {
        return spiderService.getRunHistory(name);
    }
}
