package com.hotelbooking.booking.dto;

import com.hotelbooking.booking.entity.Role;

public record UserDto(Long id, String username, Role role) {
}
