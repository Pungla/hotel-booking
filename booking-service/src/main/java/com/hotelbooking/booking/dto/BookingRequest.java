package com.hotelbooking.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Запрос на бронирование.
 * autoSelect=true - номер подбирается автоматически (roomId игнорируется, можно указать hotelId).
 * requestId - ключ идемпотентности: при повторной отправке того же запроса новая бронь не создаётся.
 */
public record BookingRequest(
        Long roomId,
        Long hotelId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean autoSelect,
        String requestId) {
}
