package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.AvailabilityRequest;
import com.hotelbooking.hotel.dto.AvailabilityResponse;
import com.hotelbooking.hotel.dto.ReleaseRequest;
import com.hotelbooking.hotel.dto.ReleaseResponse;
import com.hotelbooking.hotel.dto.RoomDto;
import com.hotelbooking.hotel.dto.RoomRequest;
import com.hotelbooking.hotel.dto.RoomStatsDto;
import com.hotelbooking.hotel.service.AvailabilityService;
import com.hotelbooking.hotel.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Номера")
public class RoomController {

    private final RoomService roomService;
    private final AvailabilityService availabilityService;

    @GetMapping
    @Operation(summary = "Свободные номера (USER, ADMIN). Если даты не заданы - все номера в работе")
    public List<RoomDto> findFree(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return roomService.findFree(hotelId, startDate, endDate);
    }

    @GetMapping("/recommend")
    @Operation(summary = "Рекомендованные номера: свободные, по возрастанию times_booked (USER, ADMIN)")
    public List<RoomDto> recommend(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return roomService.recommend(hotelId, startDate, endDate);
    }

    @GetMapping("/stats")
    @Operation(summary = "Статистика загруженности номеров (ADMIN)")
    public List<RoomStatsDto> stats() {
        return roomService.stats();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Номер по id (USER, ADMIN)")
    public RoomDto findById(@PathVariable Long id) {
        return roomService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Добавить номер в отель (ADMIN)")
    public RoomDto create(@Valid @RequestBody RoomRequest request) {
        return roomService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить номер (ADMIN)")
    public RoomDto update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return roomService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить номер (ADMIN)")
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }

    @PostMapping("/{id}/confirm-availability")
    @Operation(summary = "Подтвердить доступность и временно заблокировать номер на даты (INTERNAL)")
    public AvailabilityResponse confirmAvailability(@PathVariable Long id,
                                                    @Valid @RequestBody AvailabilityRequest request) {
        return availabilityService.confirm(id, request);
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Компенсация: снять блокировку номера (INTERNAL)")
    public ReleaseResponse release(@PathVariable Long id, @Valid @RequestBody ReleaseRequest request) {
        return availabilityService.release(id, request);
    }
}
