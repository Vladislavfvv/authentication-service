package com.innowise.authenticationservice.config;

import java.io.IOException;
import java.util.Collections;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.innowise.authenticationservice.security.JwtTokenProvider;

/**
 * Фильтр для аутентификации на основе JWT токенов.
 * Этот фильтр обрабатывает каждый входящий HTTP-запрос и проверяет наличие валидного JWT токена
 * в заголовке Authorization. Если токен присутствует и валиден, фильтр извлекает информацию
 * о пользователе (имя пользователя и роль) из токена и устанавливает аутентификацию в контексте
 * Spring Security, позволяя другим компонентам приложения идентифицировать текущего пользователя.
 * Извлекает JWT токен из заголовка Authorization (формат: "Bearer &lt;token&gt;")
 * Проверяет валидность токена с помощью JwtTokenProvider (подпись, срок действия)
 * Если токен валиден, извлекает имя пользователя и роль из токена
 * Создает объект аутентификации и устанавливает его в SecurityContext
 * Если токен невалиден или отсутствует, запрос продолжает обработку без аутентификации
 * Особенности:
 * Наследуется от OncePerRequestFilter, что гарантирует выполнение фильтра только один раз
 * для каждого запроса, даже если он проходит через несколько цепочек фильтров
 * При ошибке обработки токена (например, истек срок действия) контекст безопасности
 * очищается, но запрос не блокируется - дальнейшая авторизация будет обработана Spring Security
 * Фильтр не блокирует запросы без токена - это позволяет обрабатывать публичные эндпоинты
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // Провайдер для работы с JWT токенами (валидация, извлечение данных).
    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // Основной метод фильтра, который вызывается для каждого HTTP-запроса.
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Извлекаем JWT токен из заголовка Authorization запроса
        // Формат заголовка: "Authorization: Bearer <token>"
        String token = getTokenFromRequest(request);

        // Проверяем, что токен присутствует и валиден (подпись корректна, срок действия не истек)
        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                // Извлекаем имя пользователя (email) из токена
                String username = jwtTokenProvider.getUsernameFromToken(token);
                // Извлекаем роль пользователя из токена (например, "ROLE_USER" или "ROLE_ADMIN")
                String role = jwtTokenProvider.getRoleFromToken(token);

                // Создаем объект аутентификации Spring Security
                // Второй параметр (null) - это credentials (пароль), который не нужен для JWT
                // Третий параметр - список ролей пользователя
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null,
                                Collections.singletonList(new SimpleGrantedAuthority(role)));

                // Устанавливаем аутентификацию в контекст Spring Security
                // Теперь другие компоненты приложения могут получить текущего пользователя через
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // Если при извлечении данных из токена произошла ошибка (например, токен поврежден),
                // очищаем контекст безопасности - запрос будет обработан как неаутентифицированный
                SecurityContextHolder.clearContext();
            }
        }

        // Продолжаем выполнение цепочки фильтров
        // Запрос передается дальше независимо от того, был ли установлен контекст аутентификации
        filterChain.doFilter(request, response);
    }

    // Извлекает JWT токен из заголовка Authorization HTTP-запроса.
    private String getTokenFromRequest(HttpServletRequest request) {
        // Получаем значение заголовка Authorization из запроса
        String bearerToken = request.getHeader("Authorization");

        // Проверяем, что заголовок присутствует и начинается с "Bearer "
        // Если условие выполнено, извлекаем токен, удаляя префикс "Bearer " (7 символов)
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // Если заголовок отсутствует или имеет неверный формат, возвращаем null
        return null;
    }
}
