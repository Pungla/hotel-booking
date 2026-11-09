package com.hotelbooking.booking.mapper;

import com.hotelbooking.booking.dto.UserDto;
import com.hotelbooking.booking.entity.User;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {

    UserDto toDto(User user);

    List<UserDto> toDto(List<User> users);
}
