package com.hotelbooking.hotel.dto;

public record ReleaseResponse(
        Long roomId,
        Long bookingId,
        // false - снимать было нечего (уже сняли раньше или блокировки не было)
        boolean released) {
}
