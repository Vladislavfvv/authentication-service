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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.innowise.authenticationservice.security.JwtTokenProvider;

@Configuration
//Spring Security конфигурация для аутентификации и авторизации
@EnableWebSecurity
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    //JwtTokenProvider для генерации и валидации JWT токенов

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }
    //SecurityFilterChain для настройки безопасности HTTP запросов
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                //CORS для разрешения запросов из разных доменов
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                //CSRF для защиты от межсайтовых запросов
                .csrf(csrf -> csrf.disable())
                //Сессии в состоянии без сохранения состояния (stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //Авторизация HTTP запросов
                .authorizeHttpRequests(auth -> auth
                        //Разрешаем доступ к эндпоинтам без аутентификации
                        .requestMatchers("/auth/login", "/auth/register", "/auth/create-token", "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/auth/users/profile").permitAll()
                        .requestMatchers("/auth/**").authenticated()
                        .anyRequest().authenticated()
                )//Фильтр для аутентификации JWT токенов
                .addFilterBefore(jwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    //Фильтр для аутентификации JWT токенов
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    //CORS для разрешения запросов из разных доменов
    //Разрешаем все методы, заголовки и креды для всех путей
    //Создаем CorsConfigurationSource для настройки CORS
    public CorsConfigurationSource corsConfigurationSource() {
        //Создаем CorsConfiguration для настройки CORS
        CorsConfiguration configuration = new CorsConfiguration();
        //Разрешаем все домены в production указать конкретные домены
        configuration.setAllowedOriginPatterns(List.of("*")); 
        //Разрешаем все методы
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        //Разрешаем все заголовки
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
