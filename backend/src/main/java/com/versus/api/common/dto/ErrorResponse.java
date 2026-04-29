package com.versus.api.common.dto;

public record ErrorResponse(String error, String message, int status) { }
