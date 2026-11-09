package com.hotelbooking.hotel.dto;

public record RoomDto(
        Long id,
        Long hotelId,
        String hotelName,
        String number,
        boolean available,
        int timesBooked) {
}
