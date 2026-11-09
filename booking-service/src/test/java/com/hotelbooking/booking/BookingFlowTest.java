package com.hotelbooking.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.repository.BookingRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Двухшаговое бронирование. hotel-service заменён заглушкой WireMock,
 * так можно проверить ошибки, тайм-ауты и повторы.
 * В настройках тестов: тайм-аут 500 мс, 3 попытки, пауза 50 мс -> 100 мс.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class BookingFlowTest {

    private static final WireMockServer hotel = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        hotel.start();
    }

    @DynamicPropertySource
    static void hotelUrl(DynamicPropertyRegistry registry) {
        registry.add("hotel.url", hotel::baseUrl);
    }

    @AfterAll
    static void stopHotel() {
        hotel.stop();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BookingRepository bookingRepository;

    @BeforeEach
    void setUp() {
        hotel.resetAll();
        bookingRepository.deleteAll();
    }

    @Test
    void successConfirmsBooking() throws Exception {
        hotel.stubFor(confirm(7).willReturn(okJson("{\"status\":\"ACTIVE\"}")));

        book(user(1), 7L, "req-ok")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(7));

        // в hotel-service ушёл сервисный токен и сквозные идентификаторы
        hotel.verify(1, postRequestedFor(urlPathEqualTo("/api/rooms/7/confirm-availability"))
                .withHeader("Authorization", matching("Bearer .+"))
                .withHeader("X-Booking-Id", matching("\\d+"))
                .withRequestBody(containing("\"requestId\":\"req-ok:7\"")));
        hotel.verify(0, postRequestedFor(urlPathEqualTo("/api/rooms/7/release")));
        assertThat(onlyBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void remoteErrorCancelsBookingWithCompensation(CapturedOutput output) throws Exception {
        hotel.stubFor(confirm(7).willReturn(serverError()));
        hotel.stubFor(release(7).willReturn(okJson("{\"roomId\":7,\"bookingId\":1,\"released\":false}")));

        book(user(1), 7L, "req-500")
                .andExpect(status().isServiceUnavailable());

        // 3 попытки, потом компенсация
        hotel.verify(3, postRequestedFor(urlPathEqualTo("/api/rooms/7/confirm-availability")));
        hotel.verify(1, postRequestedFor(urlPathEqualTo("/api/rooms/7/release")));
        assertThat(onlyBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // в логах видны переходы состояний и результат компенсации
        assertThat(output.getOut())
                .contains("PENDING, номер 7")
                .contains("попытка 3/3 не удалась")
                .contains("PENDING -> CANCELLED")
                .contains("Компенсация для бронирования");
    }

    @Test
    void timeoutCancelsBookingAndReleasesSlot() throws Exception {
        // hotel-service отвечает дольше тайм-аута (500 мс)
        hotel.stubFor(confirm(7).willReturn(okJson("{}").withFixedDelay(1500)));
        hotel.stubFor(release(7).willReturn(okJson("{\"roomId\":7,\"bookingId\":1,\"released\":true}")));

        book(user(1), 7L, "req-timeout")
                .andExpect(status().isServiceUnavailable());

        hotel.verify(3, postRequestedFor(urlPathEqualTo("/api/rooms/7/confirm-availability")));
        // блокировка могла успеть встать - снимаем её
        hotel.verify(1, postRequestedFor(urlPathEqualTo("/api/rooms/7/release")));
        assertThat(onlyBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void retryAfterTemporaryErrorSucceeds() throws Exception {
        hotel.stubFor(confirm(7).inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("up"));
        hotel.stubFor(confirm(7).inScenario("retry").whenScenarioStateIs("up")
                .willReturn(okJson("{\"status\":\"ACTIVE\"}")));

        book(user(1), 7L, "req-retry")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // оба вызова с одним и тем же ключом - hotel-service не создаст вторую блокировку
        hotel.verify(2, postRequestedFor(urlPathEqualTo("/api/rooms/7/confirm-availability"))
                .withRequestBody(containing("\"requestId\":\"req-retry:7\"")));
        hotel.verify(0, postRequestedFor(urlPathEqualTo("/api/rooms/7/release")));
    }

    @Test
    void occupiedRoomReturns409WithoutRetries() throws Exception {
        hotel.stubFor(confirm(7).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Номер 7 уже занят на эти даты\"}")));

        book(user(1), 7L, "req-busy")
                .andExpect(status().isConflict());

        hotel.verify(1, postRequestedFor(urlPathEqualTo("/api/rooms/7/confirm-availability")));
        hotel.verify(0, postRequestedFor(urlPathEqualTo("/api/rooms/7/release")));
        assertThat(onlyBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void repeatedRequestDoesNotCreateDuplicate() throws Exception {
        hotel.stubFor(confirm(7).willReturn(okJson("{\"status\":\"ACTIVE\"}")));

        String first = book(user(1), 7L, "req-same")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // клиент не дождался ответа и отправил то же самое ещё раз
        String second = book(user(1), 7L, "req-same")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("id")).isEqualTo(objectMapper.readTree(first).get("id"));
        assertThat(bookingRepository.count()).isEqualTo(1);
        hotel.verify(1, postRequestedFor(urlPathEqualTo("/api/rooms/7/confirm-availability")));
    }

    @Test
    void concurrentDuplicatesCreateOneBooking() throws Exception {
        // небольшая задержка, чтобы запросы точно пересеклись
        hotel.stubFor(confirm(7).willReturn(okJson("{\"status\":\"ACTIVE\"}").withFixedDelay(200)));

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> codes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            codes.add(pool.submit(() -> {
                start.await();
                return book(user(1), 7L, "req-parallel").andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : codes) {
            statuses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).containsOnly(201, 200);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        hotel.verify(1, postRequestedFor(urlPathEqualTo("/api/rooms/7/confirm-availability")));
    }

    @Test
    void autoSelectTakesLeastBookedRoom() throws Exception {
        // hotel-service уже отдаёт номера по возрастанию times_booked
        hotel.stubFor(get(urlPathEqualTo("/api/rooms/recommend")).willReturn(okJson("""
                [{"id":5,"hotelId":1,"hotelName":"Отель","number":"105","available":true,"timesBooked":0},
                 {"id":2,"hotelId":1,"hotelName":"Отель","number":"102","available":true,"timesBooked":3}]
                """)));
        hotel.stubFor(confirm(5).willReturn(okJson("{\"status\":\"ACTIVE\"}")));

        autoBook(user(1), "req-auto")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(5))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        hotel.verify(1, getRequestedFor(urlPathEqualTo("/api/rooms/recommend")));
    }

    @Test
    void autoSelectTriesNextRoomIfFirstIsTaken() throws Exception {
        hotel.stubFor(get(urlPathEqualTo("/api/rooms/recommend")).willReturn(okJson("""
                [{"id":5,"hotelId":1,"hotelName":"Отель","number":"105","available":true,"timesBooked":0},
                 {"id":2,"hotelId":1,"hotelName":"Отель","number":"102","available":true,"timesBooked":3}]
                """)));
        hotel.stubFor(confirm(5).willReturn(aResponse().withStatus(409)));
        hotel.stubFor(confirm(2).willReturn(okJson("{\"status\":\"ACTIVE\"}")));

        autoBook(user(1), "req-auto2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(2));
    }

    @Test
    void userCancelsBookingAndSlotIsReleased() throws Exception {
        hotel.stubFor(confirm(7).willReturn(okJson("{\"status\":\"ACTIVE\"}")));
        hotel.stubFor(release(7).willReturn(okJson("{\"roomId\":7,\"bookingId\":1,\"released\":true}")));
        Long id = idOf(book(user(1), 7L, "req-cancel").andExpect(status().isCreated()));

        mockMvc.perform(MockMvcRequestBuilders.delete("/booking/" + id).with(user(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // повторная отмена ничего не ломает и в hotel-service не ходит
        mockMvc.perform(MockMvcRequestBuilders.delete("/booking/" + id).with(user(1)))
                .andExpect(status().isOk());

        hotel.verify(1, postRequestedFor(urlPathEqualTo("/api/rooms/7/release")));
    }

    @Test
    void userSeesOnlyOwnBookings() throws Exception {
        hotel.stubFor(confirm(7).willReturn(okJson("{\"status\":\"ACTIVE\"}")));
        Long mine = idOf(book(user(1), 7L, "req-mine").andExpect(status().isCreated()));
        Long foreign = idOf(book(user(2), 7L, "req-foreign").andExpect(status().isCreated()));

        mockMvc.perform(MockMvcRequestBuilders.get("/bookings").with(user(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine));

        mockMvc.perform(MockMvcRequestBuilders.get("/booking/" + foreign).with(user(1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongDatesReturn400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/booking").with(user(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":7,\"startDate\":\"2027-06-05\",\"endDate\":\"2027-06-01\"}"))
                .andExpect(status().isBadRequest());
        assertThat(bookingRepository.count()).isZero();
    }

    private ResultActions book(RequestPostProcessor user, Long roomId, String requestId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/booking")
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"roomId": %d, "startDate": "2027-06-01", "endDate": "2027-06-05",
                         "autoSelect": false, "requestId": "%s"}
                        """.formatted(roomId, requestId)));
    }

    private ResultActions autoBook(RequestPostProcessor user, String requestId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/booking")
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"roomId": 999, "startDate": "2027-06-01", "endDate": "2027-06-05",
                         "autoSelect": true, "requestId": "%s"}
                        """.formatted(requestId)));
    }

    private static MappingBuilder confirm(long roomId) {
        return post(urlPathEqualTo("/api/rooms/" + roomId + "/confirm-availability"));
    }

    private static MappingBuilder release(long roomId) {
        return post(urlPathEqualTo("/api/rooms/" + roomId + "/release"));
    }

    private static RequestPostProcessor user(long id) {
        return jwt().jwt(j -> j.subject("user" + id).claim("uid", id).claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Booking onlyBooking() {
        List<Booking> all = bookingRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private Long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
