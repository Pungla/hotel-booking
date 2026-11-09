package com.hotelbooking.booking.entity;

/**
 * PENDING -> CONFIRMED, если hotel-service подтвердил номер;
 * PENDING -> CANCELLED при ошибке/тайм-ауте (с компенсацией) или занятом номере;
 * CONFIRMED -> CANCELLED при отмене пользователем.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
