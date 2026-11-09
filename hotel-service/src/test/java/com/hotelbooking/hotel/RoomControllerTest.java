package com.hotelbooking.hotel;

import com.hotelbooking.hotel.dto.AvailabilityRequest;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import com.hotelbooking.hotel.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static com.hotelbooking.hotel.TestData.asAdmin;
import static com.hotelbooking.hotel.TestData.asUser;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomReservationRepository reservationRepository;
    @Autowired
    private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        TestData.clean(reservationRepository, roomRepository, hotelRepository);
    }

    @Test
    void adminAddsRoom() throws Exception {
        Long hotelId = TestData.room(hotelRepository, roomRepository, "1", 0).getHotel().getId();

        mockMvc.perform(post("/api/rooms").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hotelId\": " + hotelId + ", \"number\": \"305\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("305"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0));
    }

    @Test
    void recommendSortsByTimesBookedThenId() throws Exception {
        Room busy = TestData.room(hotelRepository, roomRepository, "1", 5);
        Room free1 = TestData.room(hotelRepository, roomRepository, "2", 0);
        Room middle = TestData.room(hotelRepository, roomRepository, "3", 2);
        Room free2 = TestData.room(hotelRepository, roomRepository, "4", 0);

        mockMvc.perform(get("/api/rooms/recommend").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(
                        free1.getId().intValue(), free2.getId().intValue(),
                        middle.getId().intValue(), busy.getId().intValue())));
    }

    @Test
    void roomsOutOfServiceAreNotListed() throws Exception {
        Room ok = TestData.room(hotelRepository, roomRepository, "1", 0);
        Room repair = TestData.room(hotelRepository, roomRepository, "2", 0);
        repair.setAvailable(false);
        roomRepository.save(repair);

        mockMvc.perform(get("/api/rooms").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(ok.getId().intValue())));
    }

    @Test
    void freeRoomsDependOnDates() throws Exception {
        Room booked = TestData.room(hotelRepository, roomRepository, "1", 0);
        Room other = TestData.room(hotelRepository, roomRepository, "2", 0);
        availabilityService.confirm(booked.getId(), new AvailabilityRequest(
                "req-1", 1L, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15)));

        // даты пересекаются - занятого номера в списке нет
        mockMvc.perform(get("/api/rooms").with(asUser())
                        .param("startDate", "2027-01-12").param("endDate", "2027-01-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(other.getId().intValue())));

        // заезд в день выезда предыдущего гостя - номер свободен
        mockMvc.perform(get("/api/rooms").with(asUser())
                        .param("startDate", "2027-01-15").param("endDate", "2027-01-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(
                        booked.getId().intValue(), other.getId().intValue())));
    }

    @Test
    void wrongDatesReturn400() throws Exception {
        mockMvc.perform(get("/api/rooms").with(asUser())
                        .param("startDate", "2027-01-15").param("endDate", "2027-01-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsOnlyForAdmin() throws Exception {
        Room room = TestData.room(hotelRepository, roomRepository, "1", 0);
        availabilityService.confirm(room.getId(), new AvailabilityRequest(
                "req-1", 1L, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 4)));

        mockMvc.perform(get("/api/rooms/stats").with(asUser()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/rooms/stats").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timesBooked").value(1))
                .andExpect(jsonPath("$[0].activeReservations").value(1))
                .andExpect(jsonPath("$[0].bookedNights").value(3));
    }
}
