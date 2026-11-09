package com.hotelbooking.hotel.mapper;

import com.hotelbooking.hotel.dto.HotelDto;
import com.hotelbooking.hotel.dto.HotelRequest;
import com.hotelbooking.hotel.entity.Hotel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper
public interface HotelMapper {

    HotelDto toDto(Hotel hotel);

    List<HotelDto> toDto(List<Hotel> hotels);

    @Mapping(target = "id", ignore = true)
    Hotel toEntity(HotelRequest request);

    @Mapping(target = "id", ignore = true)
    void update(@MappingTarget Hotel hotel, HotelRequest request);
}
