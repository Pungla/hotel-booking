package com.hotelbooking.booking.service;

import com.hotelbooking.booking.dto.AuthRequest;
import com.hotelbooking.booking.dto.TokenResponse;
import com.hotelbooking.booking.dto.UserCreateRequest;
import com.hotelbooking.booking.dto.UserDto;
import com.hotelbooking.booking.dto.UserUpdateRequest;
import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.exception.ConflictException;
import com.hotelbooking.booking.exception.NotFoundException;
import com.hotelbooking.booking.exception.UnauthorizedException;
import com.hotelbooking.booking.mapper.UserMapper;
import com.hotelbooking.booking.repository.UserRepository;
import com.hotelbooking.booking.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Transactional
    public TokenResponse register(AuthRequest request) {
        User user = createUser(request.username(), request.password(), Role.USER);
        log.info("Зарегистрирован пользователь {}", user.getUsername());
        return token(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse auth(AuthRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new UnauthorizedException("Неверный логин или пароль"));
        return token(user);
    }

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userMapper.toDto(userRepository.findAll(Sort.by("id")));
    }

    @Transactional
    public UserDto create(UserCreateRequest request) {
        return userMapper.toDto(createUser(request.username(), request.password(), request.role()));
    }

    @Transactional
    public UserDto update(UserUpdateRequest request) {
        User user = getUser(request.id());
        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new ConflictException("Пользователь " + request.username() + " уже существует");
            }
            user.setUsername(request.username());
        }
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        return userMapper.toDto(user);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.delete(getUser(id));
        log.info("Пользователь {} удалён", id);
    }

    private User createUser(String username, String password, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Пользователь " + username + " уже существует");
        }
        return userRepository.save(new User(username, passwordEncoder.encode(password), role));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь " + id + " не найден"));
    }

    private TokenResponse token(User user) {
        return new TokenResponse(jwtService.createToken(user), "Bearer", jwtService.getTtlSeconds(),
                user.getUsername(), user.getRole().name());
    }
}
