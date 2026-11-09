package com.hotelbooking.hotel;

import com.hotelbooking.hotel.dto.AvailabilityRequest;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.exception.ConflictException;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Несколько потоков одновременно бронируют один номер на пересекающиеся даты:
 * успешно должен пройти ровно один, остальные получают конфликт.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentConfirmTest {

    private static final int THREADS = 10;

    @Autowired
    private AvailabilityService availabilityService;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomReservationRepository reservationRepository;

    @BeforeEach
    void setUp() {
        TestData.clean(reservationRepository, roomRepository, hotelRepository);
    }

    @Test
    void onlyOneOfConcurrentRequestsWins() throws Exception {
        Room room = TestData.room(hotelRepository, roomRepository, "101", 0);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            long bookingId = i + 1;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    availabilityService.confirm(room.getId(), new AvailabilityRequest(
                            "req-" + bookingId, bookingId,
                            LocalDate.of(2027, 5, 1).plusDays(bookingId % 3),
                            LocalDate.of(2027, 5, 6)));
                    return "ok";
                } catch (ConflictException e) {
                    return "conflict";
                }
            }));
        }
        start.countDown();

        int ok = 0;
        int conflict = 0;
        for (Future<String> f : results) {
            if (f.get(30, TimeUnit.SECONDS).equals("ok")) {
                ok++;
            } else {
                conflict++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(THREADS - 1);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getTimesBooked()).isEqualTo(1);
    }
}
