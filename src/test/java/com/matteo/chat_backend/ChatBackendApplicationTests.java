package com.matteo.chat_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ChatBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void protectedEndpointsReturnUnauthorizedWhenSessionIsMissing() throws Exception {
		mockMvc.perform(delete(
				"/api/servers/00000000-0000-0000-0000-000000000001/members/00000000-0000-0000-0000-000000000002"
		))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("{\"error\":\"Sesion no valida o caducada\"}"));
	}

	@Test
	@WithMockUser(username = "owner@example.com")
	void authenticatedDeleteReachesServerService() throws Exception {
		mockMvc.perform(delete(
				"/api/servers/00000000-0000-0000-0000-000000000001/members/00000000-0000-0000-0000-000000000002"
		))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Recurso no encontrado"))
				.andExpect(jsonPath("$.message").value("User no encontrado con id: email"));
	}

}
