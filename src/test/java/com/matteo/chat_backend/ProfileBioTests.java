package com.matteo.chat_backend;

import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "bio@example.invalid")
class ProfileBioTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    private UUID userId;

    @BeforeEach
    void seed() {
        var user = new User();
        user.setUsername("bio-user");
        user.setEmail("bio@example.invalid");
        user.setPasswordHash("test-only-hash");
        user.setBio("Original");
        userId = users.saveAndFlush(user).getId();
    }

    @Test
    void bioPersistsAndIsAvailableOnPublicProfile() throws Exception {
        update("{\"bio\":\"  Música y videojuegos  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.bio").value("Música y videojuegos"));
        entityManager.flush();
        entityManager.clear();
        assertEquals("Música y videojuegos", users.findById(userId).orElseThrow().getBio());
        mvc.perform(get("/api/users/" + userId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Música y videojuegos"));
    }

    @Test
    void oldClientsPreserveBioAndNewClientsCanClearIt() throws Exception {
        update("{\"status\":\"Disponible\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Original"));
        update("{\"bio\":\"   \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(""));
    }

    @Test
    void rejectsBioLongerThanLimitWithoutChangingStoredProfile() throws Exception {
        update("{\"bio\":\"" + "a".repeat(301) + "\"}").andExpect(status().isBadRequest());
        assertEquals("Original", users.findById(userId).orElseThrow().getBio());
    }

    private org.springframework.test.web.servlet.ResultActions update(String body) throws Exception {
        return mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
