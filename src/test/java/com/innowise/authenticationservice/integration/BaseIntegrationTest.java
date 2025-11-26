package com.innowise.authenticationservice.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Базовый класс для интеграционных тестов authentication-service.
 * Настраивает тестовое окружение с использованием Testcontainers для PostgreSQL.
 * Интеграционные тесты работают с реальной базой данных в Docker-контейнере.
 * Автоматически запускает и останавливает Docker-контейнер PostgreSQL с помощью Testcontainers
 * Динамически настраивает свойства Spring Boot на основе запущенных контейнеров
 * Поддерживает два режима работы: локальный (Testcontainers) и CI/CD (внешние сервисы)
 * Отключает Liquibase и использует Hibernate для создания схемы в тестах
 * Оптимизирует настройки HikariCP для тестовой среды
 */
@SpringBootTest
public abstract class BaseIntegrationTest {
    /**
     * Флаг для определения, использовать ли Testcontainers.
     * Если USE_TESTCONTAINERS=false, используются внешние сервисы (например, в CI/CD).
     * По умолчанию true — используем Testcontainers.
     */
    private static final boolean USE_TESTCONTAINERS =
            !"false".equalsIgnoreCase(System.getenv().getOrDefault("USE_TESTCONTAINERS", "true"));

    // Статический контейнер — создается один раз для всех тестов
    static PostgreSQLContainer<?> postgres;

    static {
        // Создаём и стартуем контейнер только если USE_TESTCONTAINERS=true
        if (USE_TESTCONTAINERS) {
            postgres = new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("auth_db_test")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withStartupAttempts(3);
            postgres.start();

            // Закрываем контейнер при завершении JVM
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (postgres != null) {
                    postgres.stop();
                }
            }));
        }
    }

    /**
     * Настраивает свойства для интеграционных тестов.
     * Динамически устанавливает параметры подключения к базе данных.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (USE_TESTCONTAINERS) {
            // Конфигурация для локальных тестов с использованием Testcontainers
            registry.add("spring.datasource.url",
                    () -> postgres.getJdbcUrl() + "?currentSchema=public");
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);

            // Отключаем Liquibase — используем Hibernate для создания схемы в тестах
            registry.add("spring.liquibase.enabled", () -> "false");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");

            // Настройки пула соединений Hikari для тестов
            registry.add("spring.datasource.hikari.max-lifetime", () -> "30000");
            registry.add("spring.datasource.hikari.idle-timeout", () -> "10000");
            registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");

            // Снижаем уровень логов Hikari в тестах
            registry.add("logging.level.com.zaxxer.hikari", () -> "ERROR");
        } else {
            // Конфигурация для CI/CD окружения (GitHub Actions и т.д.)
            String pgHost = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
            String pgPort = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
            String pgDb = System.getenv().getOrDefault("POSTGRES_DB", "auth_db");
            String pgUser = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
            String pgPass = System.getenv().getOrDefault("POSTGRES_PASSWORD", "postgres");

            String baseUrl = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb;

            registry.add("spring.datasource.url", () -> baseUrl + "?currentSchema=public");
            registry.add("spring.datasource.username", () -> pgUser);
            registry.add("spring.datasource.password", () -> pgPass);

            registry.add("spring.liquibase.enabled", () -> "false");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");

            registry.add("spring.datasource.hikari.max-lifetime", () -> "30000");
            registry.add("spring.datasource.hikari.idle-timeout", () -> "10000");
            registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        }
    }
}

