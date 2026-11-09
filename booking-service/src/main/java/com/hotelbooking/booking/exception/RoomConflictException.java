package com.hotelbooking.booking.exception;

/**
 * hotel-service ответил 409: номер занят на эти даты или не сдаётся.
 */
public class RoomConflictException extends RuntimeException {

    public RoomConflictException(String message) {
        super(message);
    }
}
