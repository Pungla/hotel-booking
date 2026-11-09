package com.hotelbooking.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Запрос от booking-service на временную блокировку номера.
 * requestId - ключ идемпотентности, bookingId - сквозной идентификатор бронирования.
 */
public record AvailabilityRequest(
        @NotBlank String requestId,
        @NotNull Long bookingId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {
}
