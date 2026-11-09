package com.hotelbooking.hotel;

import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import com.hotelbooking.hotel.repository.RoomReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.hotelbooking.hotel.TestData.asAdmin;
import static com.hotelbooking.hotel.TestData.asInternal;
import static com.hotelbooking.hotel.TestData.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Внутренние эндпойнты confirm-availability / release: блокировка, идемпотентность, компенсация.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomReservationRepository reservationRepository;

    private Room room;

    @BeforeEach
    void setUp() {
        TestData.clean(reservationRepository, roomRepository, hotelRepository);
        room = TestData.room(hotelRepository, roomRepository, "101", 0);
    }

    @Test
    void confirmBlocksRoomAndCountsBooking() throws Exception {
        confirm("req-1", 1L, "2027-03-01", "2027-03-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bookingId").value(1))
                .andExpect(jsonPath("$.repeated").value(false));

        assertThat(timesBooked()).isEqualTo(1);
    }

    @Test
    void repeatedRequestIsIdempotent() throws Exception {
        confirm("req-1", 1L, "2027-03-01", "2027-03-05").andExpect(status().isOk());
        // тот же requestId пришёл ещё раз (повтор после тайм-аута)
        confirm("req-1", 1L, "2027-03-01", "2027-03-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repeated").value(true));

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(timesBooked()).isEqualTo(1);
    }

    @Test
    void overlappingDatesReturn409() throws Exception {
        confirm("req-1", 1L, "2027-03-01", "2027-03-05").andExpect(status().isOk());

        confirm("req-2", 2L, "2027-03-04", "2027-03-08")
                .andExpect(status().isConflict());

        assertThat(timesBooked()).isEqualTo(1);
    }

    @Test
    void checkInOnCheckOutDayIsAllowed() throws Exception {
        confirm("req-1", 1L, "2027-03-01", "2027-03-05").andExpect(status().isOk());
        confirm("req-2", 2L, "2027-03-05", "2027-03-07").andExpect(status().isOk());

        assertThat(timesBooked()).isEqualTo(2);
    }

    @Test
    void roomOutOfServiceCannotBeBlocked() throws Exception {
        room.setAvailable(false);
        roomRepository.save(room);

        confirm("req-1", 1L, "2027-03-01", "2027-03-05").andExpect(status().isConflict());
    }

    @Test
    void releaseFreesSlotAndIsIdempotent() throws Exception {
        confirm("req-1", 1L, "2027-03-01", "2027-03-05").andExpect(status().isOk());

        release("rel-1", 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(true));
        assertThat(timesBooked()).isZero();

        // повторная компенсация ничего не ломает
        release("rel-1", 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(false));
        assertThat(timesBooked()).isZero();

        // после снятия блокировки даты снова можно занять
        confirm("req-2", 2L, "2027-03-01", "2027-03-05").andExpect(status().isOk());
    }

    @Test
    void releaseWithoutLockDoesNothing() throws Exception {
        release("rel-1", 42L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(false));
    }

    @Test
    void internalEndpointsAreClosedForUsersAndAdmins() throws Exception {
        String body = availabilityJson("req-1", 1L, "2027-03-01", "2027-03-05");
        mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/" + room.getId() + "/release").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"x\",\"bookingId\":1}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions confirm(String requestId, Long bookingId, String start, String end) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + room.getId() + "/confirm-availability").with(asInternal())
                .contentType(MediaType.APPLICATION_JSON)
                .content(availabilityJson(requestId, bookingId, start, end)));
    }

    private ResultActions release(String requestId, Long bookingId) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + room.getId() + "/release").with(asInternal())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + requestId + "\",\"bookingId\":" + bookingId + "}"));
    }

    private String availabilityJson(String requestId, Long bookingId, String start, String end) {
        return """
                {"requestId": "%s", "bookingId": %d, "startDate": "%s", "endDate": "%s"}
                """.formatted(requestId, bookingId, start, end);
    }

    private int timesBooked() {
        return roomRepository.findById(room.getId()).orElseThrow().getTimesBooked();
    }
}
