# Authentication Service (Сервис аутентификации и авторизации на основе JWT токенов)

## Технологии

- Java 21
- Spring Boot 3.5.6
- Spring Security
- JWT (JJWT)
- Keycloak (опционально) для управления пользователями и SSO
- PostgreSQL
- BCrypt для хеширования паролей
- Docker
- GitHub Actions для CI/CD
- SonarQube для анализа кода



### Сразу нужно скачать актуальную(последнюю) версию второго приложение User Service:
    https://github.com/Vladislavfvv/userService

Перейти в папку с приложением, в консоли запустить: 
    docker compose up --build

Этот запуск поднимет все три контейнера (Postgres для сервиса, Redis, и приложение) 

### Для запуска Authentication Service необходимо перейти в папку с приложением, в консоли запустить:
    docker compose up --build

Этот запуск поднимет все четыре контейнера (Postgres для сервиса, Postgres для Keycloak, сам Keycloak и приложение). После этого Keycloak стартует, но в нём ещё нет ни realm, ни клиентов, ни ролей — по умолчанию он пустой.

Чтобы пользоваться Keycloak сразу после поднятия контейнеров, надо прогнать скрипт (или настроить всё вручную через админ‑консоль):
    cd authentication-service
    powershell -ExecutionPolicy Bypass -File .\setup-keycloak.ps1

### 4. Создание администратора по умолчанию

Начиная с текущей версии, администратор `admin@tut.by` создаётся автоматически при первом старте `docker-compose` (скрипт лежит в `docker/init/auth-db/01-create-admin.sql`). Если нужно переинициализировать пользователя вручную, выполните команды ниже:

```bash
docker exec -i auth_db psql -U postgres -d auth_db <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO users (login, password_hash, role, first_name, last_name, created_at)
VALUES (
    'admin@tut.by',
    crypt('admin', gen_salt('bf')),
    'ROLE_ADMIN',
    'Admin',
    'User',
    NOW()
)
ON CONFLICT (login) DO NOTHING;
SQL
```

> **Windows (PowerShell):**
> ```powershell
> $sql = @"
> CREATE EXTENSION IF NOT EXISTS pgcrypto;
> INSERT INTO users (login, password_hash, role, first_name, last_name, created_at)
> VALUES ('admin@tut.by', crypt('admin', gen_salt('bf')), 'ROLE_ADMIN', 'Admin', 'User', NOW())
> ON CONFLICT (login) DO NOTHING;
> "@
>
> docker exec -i auth_db psql -U postgres -d auth_db -v ON_ERROR_STOP=1 -c "$sql"
> ```

Теперь можно авторизоваться через `POST http://localhost:8081/auth/login`:

```json
{
  "login": "admin@tut.by",
  "password": "admin"
}
```

### Настройка Keycloak(проверить и если необходимо донастроить) 

    Выполните следующие шаги:

    1. Дождитесь запуска Keycloak (порт 8090)
    2. Войдите в Admin Console: `http://localhost:8090` (admin/admin)
    Далее п3-5 можно пропустить если автоматически создался реалм.
    3. Создайте Realm `authentication-service` - если не создался
    4. Создайте Client `authentication-service-client` с включенной Client authentication
    5. Создайте роли `ROLE_USER` и `ROLE_ADMIN` в Realm
    6. Проверьте также должен создаться клиент user-service-client
    7. Проверьте настройки Clients: `authentication-service-client`
    Realm settings->Login:
    User registration->on
    Forgot password->on
    Remember me->on
    Login with email->on
    Edit username->on

    User registration: должны быть добавлены Assign role(если нет, то подпишите):
    manage-account
    view-profile

    8. Скопируйте Client Secret и обновите переменные окружения
    Clients->authentication-service-client ->Credentials->Client Secret (n3mJkSY8O1HDhLZGz4sOmHnmgd6SAM5t) - вот его сверить и скопировать



## Функциональность

### Роли
- **ROLE_USER** - обычный пользователь (доступ только к своим ресурсам - пользователь, карты, дополнение информации о себе) 
- **ROLE_ADMIN** - администратор (доступ ко всем эндпоинтам)

### Roadmap работы программы **ROLE_USER**:

    1)Регистрируем пользователя по URL:
    POST http://localhost:8081/auth/register
    прописываем например:
    Body:  raw, JSON
{
        "login": "MyLogin@example.com",
        "password": "password",
        "firstName": "someName",
        "lastName": "someLastName",
  "role": "ROLE_USER"
}

    2) Логируемся:
    POST http://localhost:8081/auth/login
    Body:  raw, JSON
    {
      "login": "MyLogin@example.com",
      "password": "password" 
    }

    3) Получаем токен:
    POST http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
    Body x-www-form-urlencoded
    client_id       authentication-service-client
    client_secret   n3mJkSY8O1HDhLZGz4sOmHnmgd6SAM5t  это берем в настройках Кейклок.
    username        MyLogin@example.com
    password        password
    grant_type      password

    4) Копируем полученный токен из поля access_token:
    Например: Ваш_токен

    5) Вставляем его в следующий запрос(это уже запрос полетит на user-service) - по нему удостоверимся что такой пользователь есть в базе и возьмем его id из базы:
    GET http://localhost:8082/api/v1/users/me
    Auth-> Auth Type: Bearer Token
    Например: Ваш_токен

    6) Берем из ответа полученный id:
    Например полученный ответ: 
    {
      "id": 26,   <- вот он наш id
      "name": "someName",
      "surname": "someLastName",
      "birthDate": null,
      "email": "MyLogin@example.com",
      "cards": []
    }

    7) Далее варианты(не забываем в каждом запросе постоянно вставлять Auth-> Auth Type: Bearer Token Например: Ваш_токен!!!):

      --  посмотреть информацию о пользователе:
        GET http://localhost:8082/api/v1/users/me

      --  дополнить недостающую информацию о пользователе:
        PUT http://localhost:8082/api/v1/users/26
        Например
        { 
          "birthDate": "2002-01-01", <- дополнить, т.к. это поле пустое
          "email": "MyLogin@example.com",
          "cards": [  <- дополнить, т.к. карт нет
            {
              "userId": 26,
              "number": "2533567812341375",
              "holder": "someName someLastName",
              "expirationDate": "2026-12-31"
            },
            {
              "userId": 26,
              "number": "3533432187654472",
              "holder": "someName someLastName",
              "expirationDate": "2027-06-30"
            }
          ]
        }


      --  посмотреть информацию о картах пользователя:
        GET http://localhost:8082/api/v1/cards
        Выведет результат: в виде json карты пользователя



### Roadmap работы программы **ROLE_ADMIN**:

    1) Логируемся:
    POST http://localhost:8081/auth/login
    Body:  raw, JSON
    {
      "login": "admin@example.com",
      "password": "admin" 
    }

    2) Получаем токен:
    POST http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
    Body x-www-form-urlencoded
    client_id       authentication-service-client
    client_secret   n3mJkSY8O1HDhLZGz4sOmHnmgd6SAM5t  это берем в настройках Кейклок.
    username        admin@example.com
    password        admin
    grant_type      password

    3) Копируем полученный токен из поля access_token:
    Например: Ваш_токен  

    4) Далее варианты(не забываем в каждом запросе постоянно вставлять Auth-> Auth Type: Bearer Token Например: Ваш_токен!!!):
    ROLE_ADMIN предоставлен широкий спектр возможностей:

    - посмотреть всех юзеров пагинация:
    GET http://localhost:8082/api/v1/users?page=4&size=5

    - посмотреть все карты пагинация:
    GET http://localhost:8082/api/v1/cards?page=4&size=10

    - редактирование пользователя
    PUT http://localhost:8082/api/v1/users/30
    Например:
    raw: x-www-form-urlencoded
    { 
      "birthDate": "2002-01-01",
      "email": "newuser15@example.com",
      "cards": [
        {
          "userId": 30,
          "number": "2933555855349975",
          "holder": "newUser10 newUser10",
          "expirationDate": "2026-12-31"
        },
        {
          "userId": 30,
          "number": "1933465667699472",
          "holder": "newUser10 newUser10",
          "expirationDate": "2027-06-30"
        }
      ]
    }

    - удаление пользователя:
    DELETE http://localhost:8082/api/v1/users/28



#### 1. Создание токена (Create Token)

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

### Настройка Keycloak (опционально)

Если используется Keycloak, выполните следующие шаги:

1. Дождитесь запуска Keycloak (порт 8090)
2. Войдите в Admin Console: `http://localhost:8090` (admin/admin)
3. Создайте Realm `authentication-service`
4. Создайте Client `authentication-service-client` с включенной Client authentication
5. Создайте роли `ROLE_USER` и `ROLE_ADMIN` в Realm
6. Скопируйте Client Secret и обновите переменные окружения

Подробная инструкция в файле `KEYCLOAK_SETUP.md`

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
