package com.hotelbooking.booking.security;

import com.hotelbooking.booking.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Выпуск JWT. Подпись HS256 общим секретом, его же используют сервисы для проверки.
 */
@Service
public class JwtService {

    public static final String ISSUER = "booking-service";
    // токен для внутренних вызовов hotel-service живёт недолго
    private static final Duration SERVICE_TOKEN_TTL = Duration.ofMinutes(5);

    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.ttl-minutes:60}") long ttlMinutes) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .build();
        return encode(claims);
    }

    /**
     * Токен с ролью INTERNAL для вызовов confirm-availability / release.
     */
    public String createServiceToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("booking-service")
                .issuedAt(now)
                .expiresAt(now.plus(SERVICE_TOKEN_TTL))
                .claim("role", "INTERNAL")
                .build();
        return encode(claims);
    }

    public long getTtlSeconds() {
        return ttl.toSeconds();
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
