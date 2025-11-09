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

//кастомный фильтр безопасности обеспечивает автоматическую аутентификацию всех запросов, 
//которые несут валидный JWT в заголовке Authorization (Bearer токен)
//Вытягивает токен из заголовка Authorization (ищет Bearer <JWT>).
//Проверяет его через JwtTokenProvider.validateToken(token).
//Если токен валиден:
//Достаёт логин (getUsernameFromToken) и роль (getRoleFromToken).
//Создаёт объект UsernamePasswordAuthenticationToken с найденной ролью.
//Помещает его в SecurityContextHolder, тем самым помечая запрос как аутентифицированный.
//Если токен отсутствует или невалиден, фильтр ничего не «аутентифицирует». Контекст остаётся пустым, и остальные компоненты воспринимают запрос как анонимный.
//В конце всегда вызывает filterChain.doFilter, чтобы передать управление дальше.
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = getTokenFromRequest(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null,
                                Collections.singletonList(new SimpleGrantedAuthority(role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // Invalid token - clear context
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
