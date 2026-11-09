package com.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotBlank;

public record HotelRequest(
        @NotBlank String name,
        String address) {
}
