package com.hotelbooking.booking.client;

import com.hotelbooking.booking.dto.RoomDto;
import com.hotelbooking.booking.exception.HotelUnavailableException;
import com.hotelbooking.booking.exception.NotFoundException;
import com.hotelbooking.booking.exception.RoomConflictException;
import com.hotelbooking.booking.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Вызовы hotel-service. У каждого вызова тайм-аут (см. HotelClientConfig)
 * и ограниченное число повторов с растущей паузой.
 * Повторять безопасно: hotel-service по requestId понимает, что запрос уже обработан.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelClient {

    private final RestClient hotelRestClient;
    private final HotelClientProperties properties;
    private final JwtService jwtService;

    public record AvailabilityRequest(String requestId, Long bookingId, LocalDate startDate, LocalDate endDate) {
    }

    public record ReleaseRequest(String requestId, Long bookingId) {
    }

    public record ReleaseResponse(Long roomId, Long bookingId, boolean released) {
    }

    /**
     * Шаг 2 бронирования: попросить hotel-service заблокировать номер на даты.
     */
    public void confirmAvailability(Long roomId, Long bookingId, String requestId, LocalDate start, LocalDate end) {
        AvailabilityRequest body = new AvailabilityRequest(requestId, bookingId, start, end);
        withRetry("confirm-availability", () -> hotelRestClient.post()
                .uri("/api/rooms/{id}/confirm-availability", roomId)
                .headers(h -> internalHeaders(h, bookingId, requestId))
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * Компенсация: снять блокировку номера. Если снимать нечего, hotel-service вернёт released=false.
     */
    public boolean release(Long roomId, Long bookingId, String requestId) {
        ReleaseRequest body = new ReleaseRequest(requestId, bookingId);
        ReleaseResponse response = withRetry("release", () -> hotelRestClient.post()
                .uri("/api/rooms/{id}/release", roomId)
                .headers(h -> internalHeaders(h, bookingId, requestId))
                .body(body)
                .retrieve()
                .body(ReleaseResponse.class));
        return response != null && response.released();
    }

    /**
     * Рекомендованные номера (свободные, по возрастанию times_booked).
     * Тут передаём токен самого пользователя - это обычный пользовательский запрос.
     */
    public List<RoomDto> recommend(Long hotelId, LocalDate start, LocalDate end, String userToken) {
        List<RoomDto> rooms = withRetry("recommend", () -> hotelRestClient.get()
                .uri(b -> b.path("/api/rooms/recommend")
                        .queryParamIfPresent("hotelId", Optional.ofNullable(hotelId))
                        .queryParam("startDate", start)
                        .queryParam("endDate", end)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RoomDto>>() {
                }));
        return rooms == null ? List.of() : rooms;
    }

    private void internalHeaders(HttpHeaders headers, Long bookingId, String requestId) {
        headers.setBearerAuth(jwtService.createServiceToken());
        headers.set("X-Booking-Id", String.valueOf(bookingId));
        headers.set("X-Request-Id", requestId);
    }

    private <T> T withRetry(String action, Supplier<T> call) {
        long delay = properties.getBackoffMs();
        int max = properties.getMaxAttempts();
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException e) {
                // ошибки 4xx повторять бессмысленно
                if (e.getStatusCode().value() == 409) {
                    throw new RoomConflictException(errorMessage(e, "Номер занят на эти даты"));
                }
                if (e.getStatusCode().value() == 404) {
                    throw new NotFoundException(errorMessage(e, "Номер не найден"));
                }
                throw new IllegalArgumentException("hotel-service: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
            } catch (HttpServerErrorException | ResourceAccessException | IllegalStateException e) {
                // 5xx, тайм-аут, обрыв соединения, нет живых экземпляров в Eureka
                log.warn("{}: попытка {}/{} не удалась: {}", action, attempt, max, e.getMessage());
                if (attempt >= max) {
                    throw new HotelUnavailableException(
                            "hotel-service недоступен (" + action + "), попыток: " + max, e);
                }
                sleep(delay);
                delay = (long) (delay * properties.getBackoffMultiplier());
            }
        }
    }

    private String errorMessage(HttpClientErrorException e, String fallback) {
        // hotel-service отдаёт ошибку в виде {"message": "..."}
        try {
            Map<?, ?> error = e.getResponseBodyAs(Map.class);
            if (error != null && error.get("message") != null) {
                return error.get("message").toString();
            }
        } catch (RuntimeException ignored) {
            // тело не JSON - берём текст по умолчанию
        }
        return fallback;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HotelUnavailableException("Повтор прерван", e);
        }
    }
}
