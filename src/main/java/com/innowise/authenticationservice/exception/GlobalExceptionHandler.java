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

@RestControllerAdvice
public class GlobalExceptionHandler {
//Обработка исключений для аутентификации и авторизации
    @ExceptionHandler(com.innowise.authenticationservice.exception.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            final com.innowise.authenticationservice.exception.AuthenticationException e) {
        return buildErrorResponse("AUTHENTICATION_ERROR", e.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    //Обработка исключений для Spring Authentication
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleSpringAuthenticationException(final AuthenticationException e) {
        return buildErrorResponse("AUTHENTICATION_ERROR", "Authentication failed: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    //Обработка исключений для BadCredentialsException
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(final BadCredentialsException e) {
        return buildErrorResponse("BAD_CREDENTIALS", "Invalid login or password", HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(final AccessDeniedException e) {
        return buildErrorResponse("ACCESS_DENIED", "Access denied. Insufficient permissions.", HttpStatus.FORBIDDEN);
    }

    //Обработка исключений для InsufficientAuthenticationException - нет аутентификации
    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientAuthenticationException(
            final InsufficientAuthenticationException e) {
        return buildErrorResponse("INSUFFICIENT_AUTHENTICATION", "Authentication required", HttpStatus.UNAUTHORIZED);
    }

    //Обработка исключений для ExpiredJwtException - срок действия токена истек
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponse> handleExpiredJwtException(final ExpiredJwtException e) {
        return buildErrorResponse("TOKEN_EXPIRED", "JWT token has expired", HttpStatus.UNAUTHORIZED);
    }

    //Обработка исключений для MalformedJwtException - некорректный токен
    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJwtException(final MalformedJwtException e) {
        return buildErrorResponse("INVALID_TOKEN", "Malformed JWT token", HttpStatus.UNAUTHORIZED);
    }

    //Обработка исключений для SignatureException - некорректная подпись токена
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ErrorResponse> handleSignatureException(final SignatureException e) {
        return buildErrorResponse("INVALID_TOKEN_SIGNATURE", "Invalid JWT token signature", HttpStatus.UNAUTHORIZED);
    }

    //Обработка исключений для JwtException - ошибка JWT
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(final JwtException e) {
        return buildErrorResponse("JWT_ERROR", "JWT processing error: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    //Обработка исключений для RuntimeException - ошибка времени выполнения
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    //Обработка исключений для MethodArgumentNotValidException - некорректные аргументы метода
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

    //Обработка исключений для Exception - ошибка времени выполнения
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred"
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    //Помощник для построения ответа с ошибкой для всех исключений    
    private ResponseEntity<ErrorResponse> buildErrorResponse(String code, String message, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(status.value(), message);
        return ResponseEntity.status(status).body(error);
    }
}
