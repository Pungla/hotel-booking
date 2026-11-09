package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.AvailabilityRequest;
import com.hotelbooking.hotel.dto.AvailabilityResponse;
import com.hotelbooking.hotel.dto.ReleaseRequest;
import com.hotelbooking.hotel.dto.ReleaseResponse;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.exception.ConflictException;
import com.hotelbooking.hotel.exception.NotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Шаг согласованности со стороны отелей: временная блокировка номера на даты и её снятие.
 * Оба метода идемпотентны.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public AvailabilityResponse confirm(Long roomId, AvailabilityRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new IllegalArgumentException("endDate должна быть позже startDate");
        }

        // сначала блокируем строку номера: параллельные запросы на этот номер ждут здесь,
        // поэтому проверка пересечения и вставка ниже выполняются без гонок
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Номер " + roomId + " не найден"));

        Optional<RoomReservation> existing = reservationRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            RoomReservation reservation = existing.get();
            if (!reservation.getRoom().getId().equals(roomId)) {
                throw new ConflictException("requestId " + request.requestId() + " уже использован для другого номера");
            }
            log.info("Повторный запрос requestId={}, блокировка {} уже есть, повторно не меняем",
                    request.requestId(), reservation.getId());
            return roomMapper.toResponse(reservation, true);
        }

        if (!room.isAvailable()) {
            throw new ConflictException("Номер " + roomId + " сейчас не сдаётся");
        }
        if (reservationRepository.existsOverlap(roomId, request.startDate(), request.endDate(), ReservationStatus.ACTIVE)) {
            log.info("Номер {} занят на {} - {}, отказ", roomId, request.startDate(), request.endDate());
            throw new ConflictException("Номер " + roomId + " уже занят на эти даты");
        }

        RoomReservation reservation = new RoomReservation();
        reservation.setRoom(room);
        reservation.setBookingId(request.bookingId());
        reservation.setRequestId(request.requestId());
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setCreatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        room.setTimesBooked(room.getTimesBooked() + 1);
        log.info("Номер {} заблокирован на {} - {} (times_booked={})",
                roomId, request.startDate(), request.endDate(), room.getTimesBooked());
        return roomMapper.toResponse(reservation, false);
    }

    @Transactional
    public ReleaseResponse release(Long roomId, ReleaseRequest request) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Номер " + roomId + " не найден"));

        List<RoomReservation> active = reservationRepository
                .findByRoomIdAndBookingIdAndStatus(roomId, request.bookingId(), ReservationStatus.ACTIVE);
        if (active.isEmpty()) {
            // уже сняли раньше или блокировку так и не поставили - это нормально
            log.info("Активной блокировки номера {} нет, снимать нечего", roomId);
            return new ReleaseResponse(roomId, request.bookingId(), false);
        }

        for (RoomReservation reservation : active) {
            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setReleasedAt(LocalDateTime.now());
            room.setTimesBooked(Math.max(0, room.getTimesBooked() - 1));
        }
        log.info("Блокировка номера {} снята (times_booked={})", roomId, room.getTimesBooked());
        return new ReleaseResponse(roomId, request.bookingId(), true);
    }
}
