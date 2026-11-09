package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.HotelDto;
import com.hotelbooking.hotel.dto.HotelRequest;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.ConflictException;
import com.hotelbooking.hotel.exception.NotFoundException;
import com.hotelbooking.hotel.mapper.HotelMapper;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelMapper hotelMapper;

    @Transactional(readOnly = true)
    public List<HotelDto> findAll() {
        return hotelMapper.toDto(hotelRepository.findAll(Sort.by("id")));
    }

    @Transactional(readOnly = true)
    public HotelDto findById(Long id) {
        return hotelMapper.toDto(getHotel(id));
    }

    @Transactional
    public HotelDto create(HotelRequest request) {
        Hotel hotel = hotelRepository.save(hotelMapper.toEntity(request));
        return hotelMapper.toDto(hotel);
    }

    @Transactional
    public HotelDto update(Long id, HotelRequest request) {
        Hotel hotel = getHotel(id);
        hotelMapper.update(hotel, request);
        return hotelMapper.toDto(hotel);
    }

    @Transactional
    public void delete(Long id) {
        Hotel hotel = getHotel(id);
        if (roomRepository.existsByHotelId(id)) {
            throw new ConflictException("У отеля есть номера, сначала удалите их");
        }
        hotelRepository.delete(hotel);
    }

    Hotel getHotel(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Отель " + id + " не найден"));
    }
}
