# Authentication Service

Сервис аутентификации и авторизации на основе JWT токенов.

## Технологии

- Java 21
- Spring Boot 3.5.6
- Spring Security
- JWT (JJWT)
- PostgreSQL
- BCrypt для хеширования паролей
- Docker
- GitHub Actions для CI/CD
- SonarQube для анализа кода

## Функциональность

### Роли
- **ROLE_USER** - обычный пользователь (доступ только к своим ресурсам)
- **ROLE_ADMIN** - администратор (доступ ко всем эндпоинтам)

### Эндпоинты

#### 1. Регистрация (Save User Credentials)
```
POST /auth/register
Content-Type: application/json

{
  "login": "user123",
  "password": "password123",
  "role": "ROLE_USER"
}
```

#### 2. Создание токена (Create Token)
```
POST /auth/create-token
Content-Type: application/json

{
  "login": "user123",
  "password": "password123"
}
```

Ответ:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "expiresIn": 900000
}
```

#### 3. Логин (Login)
```
POST /auth/login
Content-Type: application/json

{
  "login": "user123",
  "password": "password123"
}
```

Возвращает access и refresh токены.

#### 4. Обновление токена (Refresh Token)
```
POST /auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### 5. Валидация токена (Validate Token)
```
POST /auth/validate
Content-Type: application/json
Authorization: Bearer {accessToken}

{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

Ответ:
```json
{
  "valid": true,
  "username": "user123",
  "role": "ROLE_USER"
}
```

## Безопасность

- Пароли хешируются с помощью BCrypt с уникальной солью для каждого пароля
- JWT токены используются для авторизации всех защищенных эндпоинтов
- Access токены имеют срок действия 15 минут (900000 мс)
- Refresh токены имеют срок действия 24 часа (86400000 мс)
- Все security исключения обрабатываются и возвращают понятные сообщения об ошибках

## Запуск через Docker

### Сборка и запуск
```bash
docker-compose up --build
```

Сервис будет доступен на порту 8081.

## Конфигурация

### application.properties
```properties
server.port=8081
spring.datasource.url=jdbc:postgresql://localhost:5432/auth_db
spring.datasource.username=postgres
spring.datasource.password=postgres
jwt.secret=mySecretKeyForJWTGenerationInAuthenticationService2025
jwt.expiration=900000
jwt.refresh.expiration=86400000
```

## CI/CD

Проект настроен с GitHub Actions для:
- Автоматической сборки и тестирования
- Анализа кода через SonarQube
- Сборки Docker образа

### Необходимые секреты в GitHub:
- `SONAR_TOKEN` - токен для SonarQube
- `SONAR_HOST_URL` - URL сервера SonarQube
- `SONAR_PROJECT_KEY` - ключ проекта в SonarQube
- `DOCKER_USERNAME` - имя пользователя Docker Hub (опционально)
- `DOCKER_PASSWORD` - пароль Docker Hub (опционально)

## Структура проекта

```
src/main/java/com/innowise/authenticationservice/
├── config/
│   ├── JwtAuthenticationFilter.java    # Фильтр для JWT аутентификации
│   └── SecurityConfig.java              # Конфигурация Spring Security
├── controller/
│   └── AuthController.java              # REST контроллер для эндпоинтов
├── dto/
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── RefreshTokenRequest.java
│   ├── TokenResponse.java
│   ├── TokenValidationRequest.java
│   └── TokenValidationResponse.java
├── exception/
│   ├── AuthenticationException.java
│   ├── ErrorResponse.java
│   └── GlobalExceptionHandler.java     # Обработка всех исключений
├── model/
│   ├── Role.java                        # Enum ролей
│   └── User.java                        # Сущность пользователя
├── repository/
│   └── UserRepository.java
├── security/
│   ├── JwtTokenProvider.java            # Генерация и валидация JWT
│   └── PasswordEncoder.java             # Обертка для BCrypt
└── service/
    └── AuthService.java                 # Бизнес-логика аутентификации
```

## Использование в других сервисах

Для использования этого сервиса в других микросервисах:

1. Отправьте запрос на `/auth/validate` с токеном для проверки его валидности
2. Получите username и role из ответа
3. Проверьте права доступа на основе роли:
   - `ROLE_ADMIN` - полный доступ
   - `ROLE_USER` - доступ только к своим ресурсам

## Пример использования

```bash
# Регистрация
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"login":"user1","password":"password123","role":"ROLE_USER"}'

# Логин
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"user1","password":"password123"}'

# Валидация токена
curl -X POST http://localhost:8081/auth/validate \
  -H "Content-Type: application/json" \
  -d '{"token":"YOUR_ACCESS_TOKEN"}'

# Обновление токена
curl -X POST http://localhost:8081/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"YOUR_REFRESH_TOKEN"}'
```
