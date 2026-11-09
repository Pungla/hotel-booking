package com.hotelbooking.hotel.config;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Тестовые данные при старте, чтобы было что бронировать.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-data", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    @Override
    public void run(String... args) {
        if (hotelRepository.count() > 0) {
            return;
        }
        Hotel moscow = hotelRepository.save(new Hotel("Гостиница Москва", "Москва, ул. Тверская, 1"));
        Hotel spb = hotelRepository.save(new Hotel("Невский отель", "Санкт-Петербург, Невский пр., 10"));

        roomRepository.save(new Room(moscow, "101"));
        roomRepository.save(new Room(moscow, "102"));
        roomRepository.save(new Room(moscow, "103"));
        roomRepository.save(new Room(spb, "201"));
        roomRepository.save(new Room(spb, "202"));
        log.info("Добавлены тестовые отели и номера");
    }
}
