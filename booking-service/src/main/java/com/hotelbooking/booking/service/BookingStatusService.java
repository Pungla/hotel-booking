package com.hotelbooking.booking.service;

import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.NotFoundException;
import com.hotelbooking.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Локальные транзакции booking-service. Каждый метод - отдельная короткая транзакция,
 * удалённые вызовы внутри них не делаются.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingStatusService {

    private final BookingRepository bookingRepository;

    @Transactional
    public Booking createPending(Long userId, Long roomId, LocalDate start, LocalDate end, String requestId) {
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setRoomId(roomId);
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(LocalDateTime.now());
        booking.setRequestId(requestId);
        // flush сразу, чтобы дубль requestId упал здесь, а не при коммите
        bookingRepository.saveAndFlush(booking);
        log.info("Бронирование {} создано: PENDING, номер {}, {} - {}", booking.getId(), roomId, start, end);
        return booking;
    }

    @Transactional
    public Booking changeRoom(Long bookingId, Long roomId) {
        Booking booking = get(bookingId);
        log.info("Бронирование {}: номер {} занят, пробуем номер {}", bookingId, booking.getRoomId(), roomId);
        booking.setRoomId(roomId);
        return booking;
    }

    @Transactional
    public Booking confirm(Long bookingId) {
        Booking booking = get(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("Бронирование {} уже в статусе {}, подтверждать нечего", bookingId, booking.getStatus());
            return booking;
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        log.info("Бронирование {}: PENDING -> CONFIRMED", bookingId);
        return booking;
    }

    @Transactional
    public Booking cancel(Long bookingId, String reason) {
        Booking booking = get(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return booking;
        }
        BookingStatus old = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        log.info("Бронирование {}: {} -> CANCELLED ({})", bookingId, old, reason);
        return booking;
    }

    private Booking get(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Бронирование " + bookingId + " не найдено"));
    }
}
