# Инструкция по запуску всего ПО

## Предварительные требования

1. **Docker** и **Docker Compose** должны быть установлены и запущены
2. **Maven** должен быть установлен (для сборки приложений)
3. **Java 21** должна быть установлена

## Шаг 1: Очистка предыдущих данных 
одна команда на 3 действия(остановка + удаление контейнеров + удаление volumes и сетей)
cd E:\Innowise\Java\UserService\userService; docker compose down -v --remove-orphans

удаление образа:
docker rmi -f userservice-user-service:latest
docker rmi -f authentication-service-authentication-service:latest

после устанавливаем:
cd E:\Innowise\Java\UserService\userService; docker compose up -d --build

### 1.1. Остановка всех контейнеров

```bash
# В директории authentication-service
cd E:\Innowise\Java\4-Security\authenticationService\authentication-service
docker compose down -v

# В директории user-service
cd E:\Innowise\Java\UserService\userService
docker compose down -v
```

### 1.2. Удаление Docker volumes (опционально, для полной очистки)

```bash
# Удаление volumes для authentication-service
docker volume rm authentication-service_auth_db_data authentication-service_keycloak_db_data 2>$null

# Удаление volumes для user-service
docker volume rm userservice_pgdata userservice_redis_data 2>$null
```

### 1.3. Удаление Docker образов (опционально)

```bash
# Удаление образов сервисов
docker rmi authentication-service_authentication-service 2>$null
docker rmi userservice_user-service 2>$null
```

## Шаг 2: Создание Docker сети

**Важно**: Сеть `backend-network` определена как `external: true` в обоих `docker-compose.yml` файлах. 
Это означает, что она должна быть создана **вручную** перед запуском сервисов.

Создайте общую сеть для всех сервисов (если еще не создана):

```bash
docker network create backend-network
```

**Проверка существования сети:**
```bash
docker network ls | grep backend-network
```

Если сеть уже существует, вы увидите её в списке. Если нет - создайте командой выше.

## Шаг 3: Запуск authentication-service

### 3.1. Переход в директорию authentication-service

```bash
cd E:\Innowise\Java\4-Security\authenticationService\authentication-service
```

### 3.2. Запуск сервисов (БД, Keycloak, authentication-service)

```bash
docker compose up -d --build
```

### 3.3. Проверка статуса контейнеров

```bash
docker compose ps
```

Все контейнеры должны быть в статусе `Up (healthy)`:
- `auth_db` - база данных authentication-service
- `keycloak_db` - база данных Keycloak
- `keycloak-innowise` - Keycloak сервер
- `authentication-service` - сервис аутентификации

### 3.4. Проверка логов

```bash
# Проверка логов authentication-service
docker logs authentication-service --tail 50

# Проверка логов Keycloak
docker logs keycloak-innowise --tail 50
```

### 3.5. Проверка создания админа в auth_db

**Важно**: Админ создается автоматически через Liquibase при первом запуске приложения.

Подождите 30-60 секунд после запуска `authentication-service`, чтобы Liquibase выполнил миграции.

```bash
docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role, first_name, last_name FROM public.users WHERE login = 'admin@tut.by';"
```

Ожидаемый результат:
```
 id |    login      |    role    | first_name | last_name 
----+---------------+------------+------------+-----------
  1 | admin@tut.by  | ROLE_ADMIN | Admin      | User
```

Если админ не создался, проверьте логи:
```bash
docker logs authentication-service | grep -i "liquibase\|admin\|error"
```

### 3.6. Проверка создания админа в Keycloak

```bash
# Проверка через Keycloak Admin API (требует токен)
# Или через веб-интерфейс: http://localhost:8090
# Логин: admin (master realm)
# Пароль: admin (из env/keycloak.env)
```

В Keycloak админ должен быть:
- **Username**: `admin@tut.by`
- **Email**: `admin@tut.by`
- **Email Verified**: `true` (галочка установлена)
- **Required Actions**: пусто (нет требований подтверждения)
- **Realm Roles**: `ROLE_ADMIN`
- **Enabled**: `true`


Донастройка в кейклок:
Назначьте роли вручную через Keycloak Admin Console:
Откройте http://localhost:8090/admin
Войдите как admin/admin
Выберите realm authentication-service
Clients → authentication-service-client → Service account roles
Assign role → Filter by clients → realm-management
Выберите: manage-users, view-users, manage-realm
После назначения ролей перезапустите authentication-service:

Или при помощи скрипта python. Чтобы он заработал, нужно подключить и потом обновить python
pip install
python.exe -m pip install --upgrade pip
запустит скрипт
python setup-service-account-roles.py

docker compose restart authentication-service





## Шаг 4: Запуск user-service

### 4.1. Переход в директорию user-service

```bash
cd E:\Innowise\Java\UserService\userService
```

### 4.2. Запуск сервисов (БД, Redis, user-service)

```bash
docker compose up -d --build
```

### 4.3. Проверка статуса контейнеров

```bash
docker compose ps
```

Все контейнеры должны быть в статусе `Up (healthy)`:
- `us_db_pg` - база данных user-service
- `redis` - Redis кэш
- `user-service` - сервис пользователей

### 4.4. Проверка логов

```bash
docker logs user-service --tail 50
```

### 4.5. Проверка создания админа в us_db_pg

```bash
docker exec us_db_pg psql -U postgres -d us_db -c "SELECT id, name, surname, email FROM userservice_data.users WHERE email = 'admin@tut.by';"
```

Ожидаемый результат:
```
 id | name  | surname |    email      
----+-------+---------+---------------
  1 | Admin | User    | admin@tut.by
```

## Шаг 5: Проверка работоспособности

### 5.1. Проверка health endpoints

```bash
# authentication-service
curl http://localhost:8081/actuator/health

# user-service
curl http://localhost:8082/actuator/health
```

### 5.2. Проверка логина админа

```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"login\":\"admin@tut.by\",\"password\":\"admin\"}"
```

Ожидаемый результат: JSON с `accessToken` и `refreshToken`

### 5.3. Проверка доступа к user-service с токеном

```bash
# Получите accessToken из предыдущего запроса и используйте его:
TOKEN="ваш_access_token_здесь"

curl -X GET http://localhost:8082/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"
```

## Шаг 6: Проверка синхронизации админа

После запуска обоих сервисов, админ должен быть синхронизирован между ними:

```bash
# Проверка в auth_db
docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role FROM public.users WHERE login = 'admin@tut.by';"

# Проверка в us_db_pg
docker exec us_db_pg psql -U postgres -d us_db -c "SELECT id, email FROM userservice_data.users WHERE email = 'admin@tut.by';"
```

Оба запроса должны вернуть админа с ID=1.

## Порядок запуска (краткая версия)

1. **Очистка**: `docker compose down -v` в обеих директориях
2. **Сеть**: `docker network create backend-network` (если не существует)
3. **authentication-service**: `cd authentication-service && docker compose up -d --build`
4. **Ожидание**: Подождите 1-2 минуты, пока все контейнеры станут healthy
5. **user-service**: `cd user-service && docker compose up -d --build`
6. **Проверка**: Проверьте логи и health endpoints

## Важные замечания

1. **Порядок запуска важен**: Сначала `authentication-service`, затем `user-service`
2. **Время запуска**: Keycloak может занять 30-60 секунд для полного запуска
3. **Пароль админа**: `admin` (во всех сервисах)
4. **Email админа**: `admin@tut.by` (во всех сервисах)
5. **ID админа**: Должен быть `1` во всех базах данных

## Устранение проблем

### Проблема: Контейнеры не запускаются

```bash
# Проверьте логи
docker logs <container_name>

# Проверьте статус
docker compose ps

# Пересоздайте контейнеры
docker compose down -v
docker compose up -d --build
```

### Проблема: Админ не создается

```bash
# Проверьте SQL скрипты в:
# - authentication-service/docker/init/auth-db/01-create-admin.sql
# - user-service/docker-entrypoint-initdb.d/02-seed-admin.sql

# Проверьте логи БД
docker logs auth_db
docker logs us_db_pg
```

### Проблема: Keycloak не импортирует realm

```bash
# Проверьте файл realm.json
cat keycloak/import/authentication-service-realm.json

# Проверьте логи Keycloak
docker logs keycloak-innowise | grep -i "realm\|import\|error"
```

### Проблема: Сервисы не могут подключиться друг к другу

```bash
# Проверьте сеть
docker network inspect backend-network

# Проверьте, что оба сервиса в одной сети
docker inspect authentication-service | grep -A 10 Networks
docker inspect user-service | grep -A 10 Networks
```

какие образы есть в системе:
docker images | Select-String -Pattern "authentication|auth"













1. Как настроены JWT токены
Authentication-service (генерация токенов):
Секретный ключ: jwt.secret=mySecretKeyForJWTGenerationInAuthenticationService2025
Access токен: 15 минут
Refresh токен: 24 часа
Алгоритм: HS256 (HMAC SHA-256)
User-service (валидация токенов):
Использует тот же секретный ключ (критически важно)
Spring Security OAuth2 Resource Server автоматически валидирует токены
Извлекает роль из токена для проверки прав доступа
2. Полный flow работы
Регистрация:
1. POST /auth/v1/register → получаете токены2. POST /api/v1/users/self с токеном → создаете профиль
Вход:
1. POST /auth/v1/login → получаете токены2. Используете токен для всех запросов к user-service
Использование API:
Все запросы к user-service требуют заголовок:Authorization: Bearer {accessToken}
3. Кто что может делать
Без токена (публичные endpoints):
/auth/v1/register - регистрация
/auth/v1/login - вход
/auth/v1/refresh - обновление токена
/actuator/health - health check
С токеном (защищенные endpoints):
ROLE_USER и ROLE_ADMIN: все /api/v1/users/** и /api/v1/cards/**
Только ROLE_ADMIN: /api/cache/**
4. Важные моменты
Секретный ключ должен совпадать в обоих сервисах
В токене нет userId, используется email из claim sub
authentication-service не создает пользователя в user-service — пользователь делает это сам через /api/v1/users/self
Access токен живет 15 минут, затем используйте refresh токен
5. Примеры запросов

Регистрация:
POST http://localhost:8081/auth/v1/register
{  "login": "user@example.com",  "password": "password123",  "role": "USER"}
Получаете accessToken и refreshToken

Создание профиля:
POST http://localhost:8082/api/v1/users/selfAuthorization: 
Bearer {accessToken}{  "name": "Roma",  "surname": "Romanov"}
Email автоматически берется из токена!

Получение пользователя:
GET http://localhost:8082/api/v1/users/email?email=user@example.comAuthorization: 
Bearer {accessToken}→ Возвращает данные пользователя