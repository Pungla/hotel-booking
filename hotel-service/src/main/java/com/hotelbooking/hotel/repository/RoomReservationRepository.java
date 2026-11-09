package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.RoomReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomReservationRepository extends JpaRepository<RoomReservation, Long> {

    Optional<RoomReservation> findByRequestId(String requestId);

    List<RoomReservation> findByRoomIdAndBookingIdAndStatus(Long roomId, Long bookingId, ReservationStatus status);

    List<RoomReservation> findByStatus(ReservationStatus status);

    boolean existsByRoomId(Long roomId);

    // пересечение интервалов [start, end): конец не включается (день выезда свободен)
    @Query("""
            select count(rr) > 0 from RoomReservation rr
            where rr.room.id = :roomId
              and rr.status = :status
              and rr.startDate < :endDate
              and rr.endDate > :startDate
            """)
    boolean existsOverlap(@Param("roomId") Long roomId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate,
                          @Param("status") ReservationStatus status);
}
