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
    docker compose up -d --build

Этот запуск поднимет все три контейнера (Postgres для сервиса, Redis, и приложение) 

### Для запуска Authentication Service необходимо перейти в папку с приложением, в консоли запустить:
    docker compose up -d --build

Этот запуск поднимет все четыре контейнера (Postgres для сервиса, Postgres для Keycloak, сам Keycloak и приложение). 

Keycloak настраивается автоматически при первом запуске через импорт realm из `keycloak/import/authentication-service-realm.json` (realm, роли, клиенты и админ-пользователь создаются автоматически).

**Важно**: После первого запуска Keycloak необходимо назначить service account роли для `authentication-service-client`:
- Запустите скрипт: `python setup-service-account-roles.py`
- Или назначьте роли вручную через Admin Console (см. `STARTUP_INSTRUCTIONS.md`)

### 4. Создание администратора по умолчанию

Начиная с текущей версии, администратор `admin@tut.by` создаётся автоматически при первом старте `docker-compose`:

- `authentication-service`: скрипт `docker/init/auth-db/01-create-admin.sql`
- `user-service`: скрипт `userService/docker-entrypoint-initdb.d/02-seed-admin.sql`
- Keycloak realm `authentication-service`: импорт `keycloak/import/authentication-service-realm.json`

Если нужно переинициализировать пользователя вручную, выполните команды ниже:

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
    Content-Type: application/json
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
    Content-Type: application/json
    Body:  raw, JSON
    {
      "login": "MyLogin@example.com",
      "password": "password" 
    }

    3) Получаем токен:
    POST http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
    Content-Type: application/json
    Body x-www-form-urlencoded
    client_id       authentication-service-client
    client_secret   n3mJkSY8O1HDhLZGz4sOmHnmgd6SAM5t  это берем в настройках Кейклок.
    username        MyLogin@example.com
    password        password
    grant_type      password

    Ответ:
      ```json
      {
        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "type": "Bearer",
        "expiresIn": 900000
      }
      ```

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

    1) Авторизоваться через `POST http://localhost:8081/auth/login`:
    Content-Type: application/json
    {
      "login": "admin@tut.by",
      "password": "admin"
    }

    2) Получаем токен:
    POST http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
    Content-Type: application/json
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




    ## Безопасность

    - Пароли хешируются с помощью BCrypt с уникальной солью для каждого пароля
    - JWT токены используются для авторизации всех защищенных эндпоинтов
    - Access токены имеют срок действия 15 минут (900000 мс)
    - Refresh токены имеют срок действия 24 часа (86400000 мс)
    - Все security исключения обрабатываются и возвращают понятные сообщения об ошибках

