package com.hotelbooking.booking.config;

import com.hotelbooking.booking.entity.Role;
import com.hotelbooking.booking.entity.User;
import com.hotelbooking.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Тестовые пользователи при старте: admin/admin123 и user/user123.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-data", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        addUser("admin", "admin123", Role.ADMIN);
        addUser("user", "user123", Role.USER);
    }

    private void addUser(String username, String password, Role role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(new User(username, passwordEncoder.encode(password), role));
            log.info("Добавлен пользователь {} ({})", username, role);
        }
    }
}
