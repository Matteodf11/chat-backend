package com.matteo.chat_backend;

import com.matteo.chat_backend.dto.LoginRequest;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.exception.UnauthorizedException;
import com.matteo.chat_backend.repository.AuthSessionRepository;
import com.matteo.chat_backend.repository.UserRepository;
import com.matteo.chat_backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@ActiveProfiles("test")
@Transactional
class RememberedSessionTests {
    @Autowired AuthService auth;
    @Autowired AuthSessionRepository sessions;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private User user;

    @BeforeEach
    void seed() {
        user = new User();
        user.setUsername("session-user");
        user.setEmail("sessions@example.invalid");
        user.setPasswordHash(passwords.encode("TestPassword123"));
        users.saveAndFlush(user);
    }

    @Test
    void rememberedSessionHasNinetyDaysAndRefreshExtendsItsLifetime() {
        var response = auth.login(request(true));
        var session = sessions.findByUserAndRevokedAtIsNullOrderByLastUsedAtDesc(user).getFirst();
        assertTrue(session.getExpiresAt().isAfter(LocalDateTime.now().plusDays(89)));
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        sessions.saveAndFlush(session);
        var renewed = auth.refresh(response.getRefreshToken());
        assertTrue(session.getExpiresAt().isAfter(LocalDateTime.now().plusDays(89)));
        assertNotEquals(response.getRefreshToken(), renewed.getRefreshToken());
        assertThrows(UnauthorizedException.class, () -> auth.refresh(response.getRefreshToken()));
    }

    @Test
    void temporarySessionDoesNotBecomePersistentWhenRefreshed() {
        var response = auth.login(request(false));
        var session = sessions.findByUserAndRevokedAtIsNullOrderByLastUsedAtDesc(user).getFirst();
        var expiry = session.getExpiresAt();
        assertTrue(expiry.isBefore(LocalDateTime.now().plusDays(2)));
        auth.refresh(response.getRefreshToken());
        assertEquals(expiry, session.getExpiresAt());
    }

    @Test
    void expiredAndLoggedOutSessionsCannotBeRenewed() {
        var expired = auth.login(request(true));
        var session = sessions.findByUserAndRevokedAtIsNullOrderByLastUsedAtDesc(user).getFirst();
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        sessions.saveAndFlush(session);
        assertThrows(UnauthorizedException.class, () -> auth.refresh(expired.getRefreshToken()));
        var loggedOut = auth.login(request(true));
        auth.logout(loggedOut.getRefreshToken());
        assertThrows(UnauthorizedException.class, () -> auth.refresh(loggedOut.getRefreshToken()));
    }

    private LoginRequest request(boolean remember) {
        var request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("TestPassword123");
        request.setRememberMe(remember);
        return request;
    }
}
