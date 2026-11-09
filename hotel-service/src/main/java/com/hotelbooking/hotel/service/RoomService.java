package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.RoomDto;
import com.hotelbooking.hotel.dto.RoomRequest;
import com.hotelbooking.hotel.dto.RoomStatsDto;
import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import com.hotelbooking.hotel.exception.ConflictException;
import com.hotelbooking.hotel.exception.NotFoundException;
import com.hotelbooking.hotel.mapper.RoomMapper;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    // для рекомендаций: сначала реже бронируемые, при равенстве - по id
    private static final Sort RECOMMEND_SORT = Sort.by("timesBooked").ascending().and(Sort.by("id").ascending());

    private final RoomRepository roomRepository;
    private final RoomReservationRepository reservationRepository;
    private final HotelService hotelService;
    private final RoomMapper roomMapper;

    @Transactional(readOnly = true)
    public List<RoomDto> findFree(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return roomMapper.toDto(findFreeRooms(hotelId, startDate, endDate, Sort.by("id")));
    }

    /**
     * Те же свободные номера, но отсортированные по возрастанию times_booked.
     * Так новые брони уходят в номера, которые простаивали.
     */
    @Transactional(readOnly = true)
    public List<RoomDto> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return roomMapper.toDto(findFreeRooms(hotelId, startDate, endDate, RECOMMEND_SORT));
    }

    @Transactional(readOnly = true)
    public RoomDto findById(Long id) {
        return roomMapper.toDto(getRoom(id));
    }

    @Transactional
    public RoomDto create(RoomRequest request) {
        Room room = new Room(hotelService.getHotel(request.hotelId()), request.number());
        if (request.available() != null) {
            room.setAvailable(request.available());
        }
        return roomMapper.toDto(roomRepository.save(room));
    }

    @Transactional
    public RoomDto update(Long id, RoomRequest request) {
        Room room = getRoom(id);
        room.setHotel(hotelService.getHotel(request.hotelId()));
        room.setNumber(request.number());
        if (request.available() != null) {
            room.setAvailable(request.available());
        }
        return roomMapper.toDto(room);
    }

    @Transactional
    public void delete(Long id) {
        Room room = getRoom(id);
        if (reservationRepository.existsByRoomId(id)) {
            throw new ConflictException("По номеру есть брони, удалить нельзя (можно поставить available=false)");
        }
        roomRepository.delete(room);
    }

    /**
     * Статистика загруженности по каждому номеру.
     */
    @Transactional(readOnly = true)
    public List<RoomStatsDto> stats() {
        Map<Long, List<RoomReservation>> activeByRoom = reservationRepository.findByStatus(ReservationStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        return roomRepository.findAllByOrderByIdAsc().stream()
                .map(room -> {
                    List<RoomReservation> active = activeByRoom.getOrDefault(room.getId(), List.of());
                    long nights = active.stream()
                            .mapToLong(r -> ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate()))
                            .sum();
                    return new RoomStatsDto(room.getId(), room.getHotel().getName(), room.getNumber(),
                            room.isAvailable(), room.getTimesBooked(), active.size(), nights);
                })
                .toList();
    }

    private List<Room> findFreeRooms(Long hotelId, LocalDate startDate, LocalDate endDate, Sort sort) {
        if (startDate == null && endDate == null) {
            return roomRepository.findAvailable(hotelId, sort);
        }
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("Нужно указать обе даты, endDate позже startDate");
        }
        return roomRepository.findFree(hotelId, startDate, endDate, ReservationStatus.ACTIVE, sort);
    }

    private Room getRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Номер " + id + " не найден"));
    }
}
