package com.versus.api.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull Boolean active) { }
