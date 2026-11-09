package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.BookingDto;
import com.hotelbooking.booking.dto.BookingRequest;
import com.hotelbooking.booking.dto.RoomDto;
import com.hotelbooking.booking.security.CurrentUser;
import com.hotelbooking.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Бронирования")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/booking")
    @Operation(summary = "Создать бронирование (USER). autoSelect=true - номер подбирается автоматически. "
            + "201 - создано, 200 - повтор запроса с тем же requestId")
    public ResponseEntity<BookingDto> create(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody BookingRequest request) {
        BookingService.CreateResult result = bookingService.create(CurrentUser.from(jwt), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.booking());
    }

    @GetMapping("/bookings")
    @Operation(summary = "История бронирований текущего пользователя (USER)")
    public List<BookingDto> history(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.history(CurrentUser.from(jwt));
    }

    @GetMapping("/booking/recommend")
    @Operation(summary = "Номера для автоподбора: свободные на даты, по возрастанию times_booked (USER)")
    public List<RoomDto> recommend(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return bookingService.recommend(CurrentUser.from(jwt), hotelId, startDate, endDate);
    }

    @GetMapping("/booking/{id}")
    @Operation(summary = "Бронирование по id, только своё (USER)")
    public BookingDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.get(CurrentUser.from(jwt), id);
    }

    @DeleteMapping("/booking/{id}")
    @Operation(summary = "Отменить бронирование (USER)")
    public BookingDto cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.cancel(CurrentUser.from(jwt), id);
    }
}
