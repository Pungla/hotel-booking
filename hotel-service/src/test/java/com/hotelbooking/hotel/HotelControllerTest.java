package com.hotelbooking.hotel;

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

import static com.hotelbooking.hotel.TestData.asAdmin;
import static com.hotelbooking.hotel.TestData.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HotelControllerTest {

    private static final String HOTEL_JSON = """
            {"name": "Отель Тест", "address": "Казань, ул. Баумана, 5"}
            """;

    @Autowired
    private MockMvc mockMvc;
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
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCreateHotel() throws Exception {
        mockMvc.perform(post("/api/hotels").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON).content(HOTEL_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesHotelAndUserSeesIt() throws Exception {
        mockMvc.perform(post("/api/hotels").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(HOTEL_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Отель Тест"));

        mockMvc.perform(get("/api/hotels").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].address").value("Казань, ул. Баумана, 5"));
    }

    @Test
    void createHotelValidatesName() throws Exception {
        mockMvc.perform(post("/api/hotels").with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"address\": \"без названия\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotDeleteHotelWithRooms() throws Exception {
        Long hotelId = TestData.room(hotelRepository, roomRepository, "1", 0).getHotel().getId();

        mockMvc.perform(delete("/api/hotels/" + hotelId).with(asAdmin()))
                .andExpect(status().isConflict());
    }
}
