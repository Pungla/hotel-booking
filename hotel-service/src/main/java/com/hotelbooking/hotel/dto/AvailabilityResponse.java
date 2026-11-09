package com.hotelbooking.hotel.dto;

import java.time.LocalDate;

public record AvailabilityResponse(
        Long reservationId,
        Long roomId,
        Long bookingId,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        // true, если такой requestId уже обрабатывали и вернули прежний результат
        boolean repeated) {
}
