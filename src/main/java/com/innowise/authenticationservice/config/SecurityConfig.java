package com.innowise.authenticationservice.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// CORS импорты закомментированы - CORS обрабатывается на уровне gateway-service
// import org.springframework.web.cors.CorsConfiguration;
// import org.springframework.web.cors.CorsConfigurationSource;
// import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
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
                // CORS обрабатывается на уровне gateway-service, поэтому здесь явно отключаем
                .cors(cors -> cors.disable())
                // Отключаем CSRF защиту, так как REST API использует JWT токены, а не cookies
                // CSRF защита нужна для защиты от подделки запросов при использовании сессий и cookies
                .csrf(csrf -> csrf.disable())
                // Устанавливаем STATELESS режим - сессии не создаются и не используются
                // Это необходимо для масштабируемости и работы в микросервисной архитектуре
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Настраиваем правила авторизации для различных эндпоинтов
                .authorizeHttpRequests(auth -> auth
                        // Внутренний технический endpoint для синхронизации удаления пользователя (только для внутренних вызовов от user-service с API ключом)
                        // Должен быть первым, чтобы не перехватывался общим правилом /auth/v1/**
                        .requestMatchers(HttpMethod.DELETE, "/auth/v1/internal/sync/users/**").permitAll()
                        // Публичные эндпоинты - доступны без аутентификации
                        // /auth/v1/login - вход пользователя
                        // /auth/v1/register - регистрация нового пользователя
                        // /auth/v1/create-token - создание токена (для внутреннего использования)
                        // /auth/v1/refresh - обновление токена
                        .requestMatchers("/auth/v1/login", "/auth/v1/register", "/auth/v1/create-token", "/auth/v1/refresh").permitAll()
                        // Эндпоинты мониторинга - публичные для health check
                        // /actuator/health - проверка здоровья приложения (доступен без аутентификации)
                        // /actuator/info - информация о приложении (доступен без аутентификации)
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
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
     * CORS настройки удалены - CORS обрабатывается на уровне gateway-service.
     * Все запросы идут через gateway-service, который добавляет необходимые CORS заголовки.
     * Это предотвращает дублирование заголовков Access-Control-Allow-Origin.
     */
    // @Bean
    // public CorsConfigurationSource corsConfigurationSource() {
    //     CorsConfiguration configuration = new CorsConfiguration();
    //     configuration.setAllowedOriginPatterns(List.of("*"));
    //     configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    //     configuration.setAllowedHeaders(List.of("*"));
    //     configuration.setAllowCredentials(true);
    //     UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    //     source.registerCorsConfiguration("/**", configuration);
    //     return source;
    // }
}
