package com.innowise.authenticationservice.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

/**
 * Глобальный обработчик исключений для authentication-service.
 * Обрабатывает все исключения и возвращает стандартизированные ответы с ошибками.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Обрабатывает исключения аутентификации из нашего сервиса.
     * Возвращает HTTP 401 Unauthorized с кодом AUTHENTICATION_ERROR.
     */
    @ExceptionHandler(com.innowise.authenticationservice.exception.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            final com.innowise.authenticationservice.exception.AuthenticationException e) {
        return buildErrorResponse("AUTHENTICATION_ERROR", e.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обрабатывает исключения аутентификации из Spring Security.
     * Возвращает HTTP 401 Unauthorized с кодом AUTHENTICATION_ERROR.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleSpringAuthenticationException(final AuthenticationException e) {
        return buildErrorResponse("AUTHENTICATION_ERROR", "Authentication failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обрабатывает исключения неверных учетных данных.
     * Возвращает HTTP 401 Unauthorized с кодом BAD_CREDENTIALS.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(final BadCredentialsException e) {
        return buildErrorResponse("BAD_CREDENTIALS", "Invalid login or password", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обрабатывает исключения отказа в доступе.
     * Возвращает HTTP 403 Forbidden с кодом ACCESS_DENIED.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(final AccessDeniedException e) {
        return buildErrorResponse("ACCESS_DENIED", "Access denied. Insufficient permissions.", HttpStatus.FORBIDDEN);
    }

    /**
     * Обрабатывает исключения недостаточной аутентификации.
     * Возвращает HTTP 401 Unauthorized с кодом INSUFFICIENT_AUTHENTICATION.
     */
    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientAuthenticationException(
            final InsufficientAuthenticationException e) {
        return buildErrorResponse("INSUFFICIENT_AUTHENTICATION", "Authentication required", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обрабатывает исключения истекших JWT токенов.
     * Возвращает HTTP 401 Unauthorized с кодом TOKEN_EXPIRED.
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponse> handleExpiredJwtException(final ExpiredJwtException e) {
        return buildErrorResponse("TOKEN_EXPIRED", "JWT token has expired", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обрабатывает исключения некорректно сформированных JWT токенов.
     * Возвращает HTTP 401 Unauthorized с кодом INVALID_TOKEN.
     */
    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJwtException(final MalformedJwtException e) {
        return buildErrorResponse("INVALID_TOKEN", "Malformed JWT token", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обрабатывает исключения неверной подписи JWT токена.
     * Возвращает HTTP 401 Unauthorized с кодом INVALID_TOKEN_SIGNATURE.
     */
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ErrorResponse> handleSignatureException(final SignatureException e) {
        return buildErrorResponse("INVALID_TOKEN_SIGNATURE", "Invalid JWT token signature", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обрабатывает общие исключения JWT.
     * Возвращает HTTP 401 Unauthorized с кодом JWT_ERROR.
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(final JwtException e) {
        return buildErrorResponse("JWT_ERROR", "JWT processing error: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Обрабатывает общие исключения времени выполнения.
     * Возвращает HTTP 400 Bad Request с сообщением об ошибке.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает исключения валидации входных данных.
     * Возвращает HTTP 400 Bad Request с детальной информацией об ошибках валидации по полям.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает исключения неподдерживаемого типа контента.
     * Возвращает HTTP 415 Unsupported Media Type с понятным сообщением.
     */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return buildErrorResponse("UNSUPPORTED_MEDIA_TYPE", 
                "Content-Type must be 'application/json'. Please set Content-Type header in your request.", 
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Обрабатывает все остальные необработанные исключения.
     * Возвращает HTTP 500 Internal Server Error с общим сообщением об ошибке.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        // Логируем полную ошибку для отладки
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        log.error("Unexpected error occurred", ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred"
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Вспомогательный метод для создания стандартизированного ответа с ошибкой.
     * 
     * @param code код ошибки (например, "AUTHENTICATION_ERROR")
     * @param message сообщение об ошибке
     * @param status HTTP статус код
     * @return ResponseEntity с ErrorResponse
     */
    private ResponseEntity<ErrorResponse> buildErrorResponse(String code, String message, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(status.value(), message);
        return ResponseEntity.status(status).body(error);
    }
}
