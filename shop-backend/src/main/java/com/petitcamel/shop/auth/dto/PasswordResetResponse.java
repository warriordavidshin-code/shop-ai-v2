package com.petitcamel.shop.auth.dto;

public record PasswordResetResponse(String message, String maskedEmail) {
}
