package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.dto.AuthRequest;
import com.hotelbooking.booking.dto.TokenResponse;
import com.hotelbooking.booking.dto.UserCreateRequest;
import com.hotelbooking.booking.dto.UserDto;
import com.hotelbooking.booking.dto.UserUpdateRequest;
import com.hotelbooking.booking.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "Пользователи")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Регистрация, возвращает JWT (доступно всем)")
    public TokenResponse register(@Valid @RequestBody AuthRequest request) {
        return userService.register(request);
    }

    @PostMapping("/auth")
    @SecurityRequirements
    @Operation(summary = "Вход, возвращает JWT на 1 час (доступно всем)")
    public TokenResponse auth(@Valid @RequestBody AuthRequest request) {
        return userService.auth(request);
    }

    @GetMapping
    @Operation(summary = "Список пользователей (ADMIN)")
    public List<UserDto> findAll() {
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать пользователя (ADMIN)")
    public UserDto create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PatchMapping
    @Operation(summary = "Изменить пользователя по id из тела запроса (ADMIN)")
    public UserDto update(@Valid @RequestBody UserUpdateRequest request) {
        return userService.update(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить пользователя: DELETE /user?id=... (ADMIN)")
    public void delete(@RequestParam Long id) {
        userService.delete(id);
    }
}
