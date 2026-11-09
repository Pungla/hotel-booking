package com.hotelbooking.booking.mapper;

import com.hotelbooking.booking.dto.BookingDto;
import com.hotelbooking.booking.entity.Booking;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface BookingMapper {

    BookingDto toDto(Booking booking);

    List<BookingDto> toDto(List<Booking> bookings);
}
