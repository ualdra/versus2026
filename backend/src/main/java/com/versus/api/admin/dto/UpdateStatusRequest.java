package com.versus.api.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull Boolean active) {}
