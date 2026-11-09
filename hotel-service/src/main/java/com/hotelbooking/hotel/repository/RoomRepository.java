package com.hotelbooking.hotel.repository;

import com.hotelbooking.hotel.entity.ReservationStatus;
import com.hotelbooking.hotel.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    // блокировка строки номера (select ... for update), чтобы параллельные
    // подтверждения одного номера шли строго по очереди
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "hotel")
    @Query("select r from Room r where r.available = true and (:hotelId is null or r.hotel.id = :hotelId)")
    List<Room> findAvailable(@Param("hotelId") Long hotelId, Sort sort);

    // номера в работе, у которых нет активной брони на пересекающиеся даты
    @EntityGraph(attributePaths = "hotel")
    @Query("""
            select r from Room r
            where r.available = true
              and (:hotelId is null or r.hotel.id = :hotelId)
              and not exists (
                  select rr.id from RoomReservation rr
                  where rr.room = r
                    and rr.status = :status
                    and rr.startDate < :endDate
                    and rr.endDate > :startDate)
            """)
    List<Room> findFree(@Param("hotelId") Long hotelId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("status") ReservationStatus status,
                        Sort sort);

    @EntityGraph(attributePaths = "hotel")
    List<Room> findAllByOrderByIdAsc();

    boolean existsByHotelId(Long hotelId);
}
