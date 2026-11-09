package com.hotelbooking.booking.dto;

import com.hotelbooking.booking.entity.BookingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingDto(
        Long id,
        Long userId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        LocalDateTime createdAt,
        String requestId) {
}
