package com.hotelbooking.booking.repository;

import com.hotelbooking.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByRequestId(String requestId);

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
}
