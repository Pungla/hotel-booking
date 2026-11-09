package com.hotelbooking.hotel;

import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Общие вещи для тестов: токены с нужной ролью и подготовка данных.
 */
public final class TestData {

    private TestData() {
    }

    public static RequestPostProcessor asUser() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public static RequestPostProcessor asAdmin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    public static RequestPostProcessor asInternal() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_INTERNAL"));
    }

    public static void clean(RoomReservationRepository reservations, RoomRepository rooms, HotelRepository hotels) {
        reservations.deleteAll();
        rooms.deleteAll();
        hotels.deleteAll();
    }

    public static Room room(HotelRepository hotels, RoomRepository rooms, String number, int timesBooked) {
        Hotel hotel = hotels.findAll().stream().findFirst()
                .orElseGet(() -> hotels.save(new Hotel("Тестовый отель", "Тестовая ул., 1")));
        Room room = new Room(hotel, number);
        room.setTimesBooked(timesBooked);
        return rooms.save(room);
    }
}
