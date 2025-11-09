package com.innowise.authenticationservice;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AuthenticationServiceApplicationTests {

	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
			.withDatabaseName("auth_db_test")
			.withUsername("postgres")
			.withPassword("postgres");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Test
	void contextLoads() {
		// verifies that Spring context loads successfully with Testcontainers overrides
	}

	@AfterAll
	static void tearDown() {
		POSTGRES.stop();
	}

}
