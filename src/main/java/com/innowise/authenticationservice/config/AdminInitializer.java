package com.innowise.authenticationservice.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.innowise.authenticationservice.model.Role;
import com.innowise.authenticationservice.model.User;
import com.innowise.authenticationservice.repository.UserRepository;
import com.innowise.authenticationservice.service.KeycloakService;

/**
 * Автоматическое создание администратора при первом запуске приложения
 * Создает админа в auth_db и синхронизирует его с Keycloak
 */
@Configuration
@Profile("!test") // Не выполняется в тестах
public class AdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private static final String ADMIN_LOGIN = "admin@tut.by";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String ADMIN_FIRST_NAME = "Admin";
    private static final String ADMIN_LAST_NAME = "User";

    @Bean
    public ApplicationRunner adminInitializerRunner(
            UserRepository userRepository,
            Optional<KeycloakService> keycloakService) {
        return args -> {
            // Проверяем, существует ли админ в auth_db
            Optional<User> adminUser = userRepository.findByLogin(ADMIN_LOGIN);
            
            if (adminUser.isEmpty()) {
                log.warn("Admin user {} not found in auth_db. Admin should be created via SQL script on first startup.", ADMIN_LOGIN);
                return;
            }

            User admin = adminUser.get();
            log.info("Admin user {} found in auth_db (id: {})", ADMIN_LOGIN, admin.getId());

            // Синхронизируем админа с Keycloak, если Keycloak доступен
            keycloakService.ifPresent(service -> {
                try {
                    if (!service.userExists(ADMIN_LOGIN)) {
                        log.info("Creating admin user {} in Keycloak...", ADMIN_LOGIN);
                        service.createUser(
                                ADMIN_LOGIN,
                                ADMIN_PASSWORD,
                                Role.ROLE_ADMIN,
                                ADMIN_FIRST_NAME,
                                ADMIN_LAST_NAME
                        );
                        log.info("Admin user {} successfully created in Keycloak with emailVerified=true", ADMIN_LOGIN);
                    } else {
                        log.info("Admin user {} already exists in Keycloak", ADMIN_LOGIN);
                    }
                } catch (Exception e) {
                    log.error("Failed to create admin user {} in Keycloak: {}", ADMIN_LOGIN, e.getMessage(), e);
                    // Не выбрасываем исключение, чтобы не прерывать запуск приложения
                }
            });
        };
    }
}

