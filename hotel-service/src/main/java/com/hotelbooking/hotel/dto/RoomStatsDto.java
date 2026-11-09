package com.hotelbooking.hotel.dto;

public record RoomStatsDto(
        Long roomId,
        String hotelName,
        String number,
        boolean available,
        int timesBooked,
        // активные брони на номер сейчас
        long activeReservations,
        // сколько суток номер занят по активным броням
        long bookedNights) {
}
