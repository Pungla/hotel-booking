package com.hotelbooking.booking.exception;

/**
 * hotel-service не ответил (ошибка 5xx или тайм-аут) даже после всех повторов.
 */
public class HotelUnavailableException extends RuntimeException {

    public HotelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
