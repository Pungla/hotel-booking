package com.hotelbooking.booking.dto;

public record TokenResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String username,
        String role) {
}
