package com.hotelbooking.booking.dto;

import com.hotelbooking.booking.entity.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PATCH /user: id обязателен, остальные поля меняются, только если переданы.
 */
public record UserUpdateRequest(
        @NotNull Long id,
        @Size(min = 3, max = 50) String username,
        @Size(min = 4, max = 100) String password,
        Role role) {
}
