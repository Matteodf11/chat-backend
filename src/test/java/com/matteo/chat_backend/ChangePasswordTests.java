package com.matteo.chat_backend;

import com.matteo.chat_backend.dto.AuthResponse;
import com.matteo.chat_backend.dto.LoginRequest;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.AuthSessionRepository;
import com.matteo.chat_backend.repository.UserRepository;
import com.matteo.chat_backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChangePasswordTests {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    private User user;
    private AuthResponse session;

    @BeforeEach
    void seed() {
        user = new User();
        user.setUsername("password-user");
        user.setEmail("password@example.invalid");
        user.setPasswordHash(passwords.encode("OriginalPassword123"));
        users.saveAndFlush(user);
        session = auth.login(login("OriginalPassword123"));
    }

    @Test
    void changesPasswordAndInvalidatesAllPreviousSessions() throws Exception {
        var secondDevice = auth.login(login("OriginalPassword123"));
        change("OriginalPassword123", "UpdatedPassword456").andExpect(status().isNoContent());
        users.flush();
        assertTrue(passwords.matches("UpdatedPassword456", users.findById(user.getId()).orElseThrow().getPasswordHash()));
        assertTrue(sessions.findByUserAndRevokedAtIsNullOrderByLastUsedAtDesc(user).isEmpty());

        for (var previous : new AuthResponse[]{session, secondDevice}) {
            mvc.perform(put("/auth/password").header("Authorization", "Bearer " + previous.getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"UpdatedPassword456\",\"newPassword\":\"AnotherPassword789\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + previous.getRefreshToken() + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"password@example.invalid\",\"password\":\"OriginalPassword123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"password@example.invalid\",\"password\":\"UpdatedPassword456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void incorrectCurrentPasswordReturnsValidationErrorAndKeepsSession() throws Exception {
        change("IncorrectPassword123", "UpdatedPassword456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));
        assertUnchanged();
    }

    @Test
    void rejectsSameOrShortNewPasswordWithoutChangingCredentials() throws Exception {
        change("OriginalPassword123", "OriginalPassword123").andExpect(status().isBadRequest());
        change("OriginalPassword123", "short").andExpect(status().isBadRequest());
        assertUnchanged();
    }

    private void assertUnchanged() {
        assertTrue(passwords.matches("OriginalPassword123", user.getPasswordHash()));
        assertEquals(0, user.getTokenVersion());
        assertEquals(1, sessions.findByUserAndRevokedAtIsNullOrderByLastUsedAtDesc(user).size());
    }

    private org.springframework.test.web.servlet.ResultActions change(String current, String replacement) throws Exception {
        return mvc.perform(put("/auth/password").header("Authorization", "Bearer " + session.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + replacement + "\"}"));
    }

    private LoginRequest login(String password) {
        var request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword(password);
        request.setRememberMe(true);
        return request;
    }
}
