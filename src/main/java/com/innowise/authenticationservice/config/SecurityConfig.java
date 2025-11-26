package com.innowise.authenticationservice.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.innowise.authenticationservice.security.JwtTokenProvider;

/**
 * Конфигурация безопасности Spring Security для authentication-service.
 * Класс настраивает систему безопасности приложения, определяя правила доступа к эндпоинтам,
 * конфигурацию CORS, управление сессиями и интеграцию JWT аутентификации.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
     // Провайдер для работы с JWT токенами (валидация, создание, извлечение данных)     
    private final JwtTokenProvider jwtTokenProvider;
   
    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // Настраивает цепочку фильтров безопасности Spring Security
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Настраиваем CORS (Cross-Origin Resource Sharing) для разрешения запросов с других доменов
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Отключаем CSRF защиту, так как REST API использует JWT токены, а не cookies
                // CSRF защита нужна для защиты от подделки запросов при использовании сессий и cookies
                .csrf(csrf -> csrf.disable())
                // Устанавливаем STATELESS режим - сессии не создаются и не используются
                // Это необходимо для масштабируемости и работы в микросервисной архитектуре
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Настраиваем правила авторизации для различных эндпоинтов
                .authorizeHttpRequests(auth -> auth
                        // Публичные эндпоинты - доступны без аутентификации
                        // /auth/v1/login - вход пользователя
                        // /auth/v1/register - регистрация нового пользователя
                        // /auth/v1/create-token - создание токена (для внутреннего использования)
                        // /auth/v1/refresh - обновление токена
                        .requestMatchers("/auth/v1/login", "/auth/v1/register", "/auth/v1/create-token", "/auth/v1/refresh").permitAll()
                        // Эндпоинты мониторинга - требуют роль ADMIN
                        // /actuator/health - проверка здоровья приложения
                        // /actuator/info - информация о приложении
                        .requestMatchers("/actuator/health", "/actuator/info").hasRole("ADMIN")
                        // Все остальные эндпоинты /auth/v1/** требуют аутентификации
                        .requestMatchers("/auth/v1/**").authenticated()
                        // Любые другие запросы также требуют аутентификации
                        .anyRequest().authenticated()
                )
                // Добавляем кастомный JWT фильтр перед стандартным фильтром аутентификации Spring Security
                // Это позволяет обрабатывать JWT токены до того, как Spring Security попытается использовать
                // стандартные механизмы аутентификации (например, форму входа)
                .addFilterBefore(jwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Создает и регистрирует JWT Authentication Filter для обработки JWT токенов в HTTP-запросах.     
     * Фильтр обрабатывает каждый входящий HTTP-запрос и проверяет наличие валидного JWT токена
     * в заголовке Authorization. Если токен присутствует и валиден, фильтр извлекает информацию
     * о пользователе (имя пользователя и роль) из токена и устанавливает аутентификацию в контексте
     * Spring Security (SecurityContext), позволяя другим компонентам приложения идентифицировать
     * текущего пользователя.  
     * Извлекает JWT токен из заголовка Authorization (формат: "Bearer &lt;token&gt;")
     * Проверяет валидность токена с помощью JwtTokenProvider (подпись, срок действия)
     * Если токен валиден, извлекает имя пользователя и роль из токена
     * Создает объект аутентификации и устанавливает его в SecurityContext
     * Если токен невалиден или отсутствует, запрос продолжает обработку без аутентификации 
     */

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    /**
     * Настраивает CORS (Cross-Origin Resource Sharing) для разрешения кросс-доменных HTTP-запросов.     
     * CORS - это механизм безопасности браузеров, который позволяет веб-страницам делать запросы
     * к серверу, находящемуся на другом домене, порту или протоколе. Без правильной настройки CORS
     * браузер блокирует такие запросы из соображений безопасности.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Разрешаем запросы с любых доменов (в production укажите конкретные домены для безопасности)
        configuration.setAllowedOriginPatterns(List.of("*"));
        // Разрешаем основные HTTP-методы для REST API
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Разрешаем все заголовки (включая Authorization для JWT токенов)
        configuration.setAllowedHeaders(List.of("*"));
        // Разрешаем отправку учетных данных (cookies, authorization headers) в кросс-доменных запросах
        configuration.setAllowCredentials(true);

        // Создаем источник конфигурации CORS и применяем настройки ко всем эндпоинтам ("/**")
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
