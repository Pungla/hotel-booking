package com.hotelbooking.booking.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Данные пользователя из JWT: id и роль.
 */
public record CurrentUser(Long id, String username, boolean admin, String token) {

    public static CurrentUser from(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        Long id = uid == null ? null : Long.valueOf(uid.toString());
        return new CurrentUser(id, jwt.getSubject(), "ADMIN".equals(jwt.getClaimAsString("role")), jwt.getTokenValue());
    }
}
