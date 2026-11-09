package com.hotelbooking.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Регистрация, вход и разграничение доступа на настоящих JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // тестовые admin и user создаются при старте, остальных удаляем
        userRepository.findAll().stream()
                .filter(u -> !u.getUsername().equals("admin") && !u.getUsername().equals("user"))
                .forEach(userRepository::delete);
    }

    @Test
    void registerReturnsWorkingToken() throws Exception {
        String token = tokenFrom(register("ivan", "secret1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER")));

        mockMvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tokenLivesOneHour() throws Exception {
        String token = tokenFrom(auth("user", "user123").andExpect(status().isOk()));

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getSubject()).isEqualTo("user");
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        register("petr", "secret1").andExpect(status().isCreated());
        register("petr", "other12").andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        auth("user", "wrong-password").andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/bookings")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotManageUsers() throws Exception {
        String token = tokenFrom(auth("user", "user123"));

        mockMvc.perform(post("/user").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"password\":\"1234\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesUpdatesAndDeletesUser() throws Exception {
        String admin = "Bearer " + tokenFrom(auth("admin", "admin123"));

        String created = mockMvc.perform(post("/user").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager\",\"password\":\"pass1234\",\"role\":\"USER\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(patch("/user").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("manager"));

        // новая роль попадает в токен при следующем входе
        assertThat(jwtDecoder.decode(tokenFrom(auth("manager", "pass1234"))).getClaimAsString("role"))
                .isEqualTo("ADMIN");

        mockMvc.perform(delete("/user").param("id", String.valueOf(id)).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        auth("manager", "pass1234").andExpect(status().isUnauthorized());
    }

    private ResultActions register(String username, String password) throws Exception {
        return mockMvc.perform(post("/user/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions auth(String username, String password) throws Exception {
        return mockMvc.perform(post("/user/auth").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private String tokenFrom(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
