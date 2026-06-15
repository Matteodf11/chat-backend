package com.matteo.chat.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class UserRepositoryTests {

	@Autowired
	private UserRepository userRepository;

	@Test
	void findsUsersByEmailIgnoringCase() {
		AppUser user = new AppUser("matteo@example.com", "matteo", "Matteo", "hashed-password");
		userRepository.save(user);

		assertThat(userRepository.findByEmailIgnoreCase("MATTEO@example.com"))
				.isPresent()
				.get()
				.extracting(AppUser::getUsername)
				.isEqualTo("matteo");
	}
}
