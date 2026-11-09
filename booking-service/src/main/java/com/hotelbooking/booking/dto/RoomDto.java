package com.hotelbooking.booking.dto;

/**
 * Номер, как его отдаёт hotel-service.
 */
public record RoomDto(
        Long id,
        Long hotelId,
        String hotelName,
        String number,
        boolean available,
        int timesBooked) {
}
