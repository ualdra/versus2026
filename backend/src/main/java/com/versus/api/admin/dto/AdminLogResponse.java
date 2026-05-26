package com.versus.api.admin.dto;

import java.time.Instant;

public record AdminLogResponse(Instant ts, String level, String message) { }
