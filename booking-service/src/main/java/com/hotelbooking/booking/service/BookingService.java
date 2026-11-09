package com.hotelbooking.booking.service;

import com.hotelbooking.booking.client.HotelClient;
import com.hotelbooking.booking.dto.BookingDto;
import com.hotelbooking.booking.dto.BookingRequest;
import com.hotelbooking.booking.dto.RoomDto;
import com.hotelbooking.booking.entity.Booking;
import com.hotelbooking.booking.entity.BookingStatus;
import com.hotelbooking.booking.exception.ConflictException;
import com.hotelbooking.booking.exception.HotelUnavailableException;
import com.hotelbooking.booking.exception.NotFoundException;
import com.hotelbooking.booking.exception.RoomConflictException;
import com.hotelbooking.booking.mapper.BookingMapper;
import com.hotelbooking.booking.repository.BookingRepository;
import com.hotelbooking.booking.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Создание брони в два шага:
 * 1) локальная транзакция - бронь в статусе PENDING;
 * 2) hotel-service подтверждает и блокирует номер на даты.
 * Успех -> CONFIRMED. Номер занят -> CANCELLED. Ошибка/тайм-аут -> CANCELLED + компенсация (release).
 * Сам метод не транзакционный: удалённый вызов не держит открытую транзакцию.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    // при автоподборе пробуем не больше стольких номеров из рекомендаций
    private static final int AUTO_SELECT_CANDIDATES = 3;

    private final BookingRepository bookingRepository;
    private final BookingStatusService statusService;
    private final HotelClient hotelClient;
    private final BookingMapper bookingMapper;

    /**
     * created=false - это повтор уже обработанного запроса (тот же requestId).
     */
    public record CreateResult(BookingDto booking, boolean created) {
    }

    public CreateResult create(CurrentUser user, BookingRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new IllegalArgumentException("endDate должна быть позже startDate");
        }
        String requestId = StringUtils.hasText(request.requestId()) ? request.requestId() : UUID.randomUUID().toString();

        // запрос с таким requestId уже был - новую бронь не создаём
        Optional<Booking> existing = bookingRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return repeated(existing.get(), user);
        }

        List<Long> rooms = chooseRooms(user, request);

        Booking booking;
        try {
            booking = statusService.createPending(user.id(), rooms.get(0), request.startDate(), request.endDate(), requestId);
        } catch (DataIntegrityViolationException e) {
            // тот же запрос пришёл параллельно и успел создать бронь раньше
            Booking other = bookingRepository.findByRequestId(requestId).orElseThrow(() -> e);
            return repeated(other, user);
        }

        MDC.put("bookingId", String.valueOf(booking.getId()));
        MDC.put("requestId", requestId);
        try {
            return new CreateResult(bookingMapper.toDto(confirmWithHotel(booking, rooms)), true);
        } finally {
            MDC.remove("bookingId");
            MDC.remove("requestId");
        }
    }

    @Transactional(readOnly = true)
    public List<BookingDto> history(CurrentUser user) {
        return bookingMapper.toDto(bookingRepository.findByUserIdOrderByCreatedAtDesc(user.id()));
    }

    @Transactional(readOnly = true)
    public BookingDto get(CurrentUser user, Long id) {
        return bookingMapper.toDto(getOwned(user, id));
    }

    /**
     * Отмена пользователем: сначала снимаем блокировку в hotel-service, потом CANCELLED.
     * Если hotel-service недоступен - бронь остаётся как была, отмену можно повторить.
     */
    public BookingDto cancel(CurrentUser user, Long id) {
        Booking booking = getOwned(user, id);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return bookingMapper.toDto(booking);
        }
        MDC.put("bookingId", String.valueOf(id));
        try {
            boolean released = hotelClient.release(booking.getRoomId(), id, "cancel:" + id);
            log.info("Отмена бронирования {}: блокировка номера {} {}", id, booking.getRoomId(),
                    released ? "снята" : "уже была снята");
            return bookingMapper.toDto(statusService.cancel(id, "отменено пользователем"));
        } finally {
            MDC.remove("bookingId");
        }
    }

    public List<RoomDto> recommend(CurrentUser user, Long hotelId, LocalDate start, LocalDate end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Нужно указать startDate и endDate, endDate позже startDate");
        }
        return hotelClient.recommend(hotelId, start, end, user.token());
    }

    private List<Long> chooseRooms(CurrentUser user, BookingRequest request) {
        if (!request.autoSelect()) {
            if (request.roomId() == null) {
                throw new IllegalArgumentException("Укажите roomId или autoSelect=true");
            }
            return List.of(request.roomId());
        }
        // автоподбор: свободные номера по возрастанию times_booked, берём первые
        List<Long> ids = hotelClient.recommend(request.hotelId(), request.startDate(), request.endDate(), user.token())
                .stream()
                .map(RoomDto::id)
                .limit(AUTO_SELECT_CANDIDATES)
                .toList();
        if (ids.isEmpty()) {
            throw new ConflictException("Нет свободных номеров на эти даты");
        }
        log.info("Автоподбор номера: кандидаты {}", ids);
        return ids;
    }

    private Booking confirmWithHotel(Booking booking, List<Long> rooms) {
        Long bookingId = booking.getId();
        for (int i = 0; i < rooms.size(); i++) {
            Long roomId = rooms.get(i);
            if (i > 0) {
                booking = statusService.changeRoom(bookingId, roomId);
            }
            // свой ключ на каждый номер; при повторах он не меняется
            String hotelRequestId = booking.getRequestId() + ":" + roomId;
            try {
                hotelClient.confirmAvailability(roomId, bookingId, hotelRequestId,
                        booking.getStartDate(), booking.getEndDate());
                return statusService.confirm(bookingId);
            } catch (RoomConflictException e) {
                // номер заняли - блокировка не ставилась, компенсация не нужна
                log.info("Номер {} не подтверждён: {}", roomId, e.getMessage());
            } catch (HotelUnavailableException e) {
                // ошибка или тайм-аут после всех повторов
                statusService.cancel(bookingId, "hotel-service не ответил");
                compensate(bookingId, roomId, hotelRequestId);
                throw new HotelUnavailableException(
                        "Сервис отелей не ответил, бронирование " + bookingId + " отменено", e);
            } catch (RuntimeException e) {
                // например, номера нет (404) - отменяем и отдаём ошибку дальше
                statusService.cancel(bookingId, e.getMessage());
                throw e;
            }
        }
        statusService.cancel(bookingId, "номер занят");
        throw new ConflictException("Номер занят на эти даты, бронирование " + bookingId + " отменено");
    }

    /**
     * Компенсация: снять блокировку, если hotel-service успел её поставить
     * (например, ответ не дошёл из-за тайм-аута). Если блокировки нет - hotel-service ничего не делает.
     */
    private void compensate(Long bookingId, Long roomId, String hotelRequestId) {
        try {
            boolean released = hotelClient.release(roomId, bookingId, "release:" + hotelRequestId);
            log.info("Компенсация для бронирования {}: {}", bookingId,
                    released ? "блокировка номера " + roomId + " снята" : "блокировки не было, снимать нечего");
        } catch (RuntimeException e) {
            log.error("Компенсация для бронирования {} не выполнена, блокировка номера {} могла остаться: {}",
                    bookingId, roomId, e.getMessage());
        }
    }

    private CreateResult repeated(Booking booking, CurrentUser user) {
        if (!booking.getUserId().equals(user.id())) {
            throw new ConflictException("requestId уже использован");
        }
        log.info("Повторный запрос requestId={}: бронирование {} уже есть ({}), повторно не обрабатываем",
                booking.getRequestId(), booking.getId(), booking.getStatus());
        return new CreateResult(bookingMapper.toDto(booking), false);
    }

    private Booking getOwned(CurrentUser user, Long id) {
        return bookingRepository.findById(id)
                .filter(b -> user.admin() || b.getUserId().equals(user.id()))
                .orElseThrow(() -> new NotFoundException("Бронирование " + id + " не найдено"));
    }
}
