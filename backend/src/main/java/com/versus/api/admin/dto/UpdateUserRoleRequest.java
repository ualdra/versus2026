package com.versus.api.admin.dto;

import com.versus.api.users.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) { }
