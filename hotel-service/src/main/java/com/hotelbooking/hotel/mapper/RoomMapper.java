package com.hotelbooking.hotel.mapper;

import com.hotelbooking.hotel.dto.AvailabilityResponse;
import com.hotelbooking.hotel.dto.RoomDto;
import com.hotelbooking.hotel.entity.Room;
import com.hotelbooking.hotel.entity.RoomReservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface RoomMapper {

    @Mapping(target = "hotelId", source = "hotel.id")
    @Mapping(target = "hotelName", source = "hotel.name")
    RoomDto toDto(Room room);

    List<RoomDto> toDto(List<Room> rooms);

    @Mapping(target = "reservationId", source = "reservation.id")
    @Mapping(target = "roomId", source = "reservation.room.id")
    @Mapping(target = "status", expression = "java(reservation.getStatus().name())")
    @Mapping(target = "repeated", source = "repeated")
    AvailabilityResponse toResponse(RoomReservation reservation, boolean repeated);
}
