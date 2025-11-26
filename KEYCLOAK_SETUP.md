# Настройка Keycloak для Authentication Service

## Обзор

Authentication Service интегрирован с Keycloak для управления пользователями и ролями. При регистрации пользователя в локальной БД, он также автоматически создается в Keycloak.

## Требования

1. Keycloak сервер запущен (через docker-compose)
2. Realm `authentication-service` создан в Keycloak
3. Client `authentication-service-client` создан в Keycloak
4. Роли `ROLE_USER` и `ROLE_ADMIN` созданы в Realm

## Настройка Keycloak

### 1. Запуск Keycloak

```bash
docker compose up -d keycloak keycloak-db
```

Или для запуска всего стека:

```bash
docker compose up -d
```

Keycloak будет доступен на `http://localhost:8090`

**Примечание:** Контейнер Keycloak имеет имя `keycloak-innowise` (для избежания конфликтов), но сервис в docker-compose называется `keycloak`.

### 2. Создание Realm

1. Войдите в Keycloak Admin Console: `http://localhost:8090`
2. Логин: `admin`, Пароль: `admin`
3. Создайте новый Realm с именем `authentication-service`
4. Сохраните Realm

### 3. Создание Client

1. В Realm `authentication-service` перейдите в **Clients**
2. Нажмите **Create client**
3. Заполните:
   - **Client ID**: `authentication-service-client`
   - **Client authentication**: ON (включите для использования Client Credentials flow)
   - **Authorization**: OFF (можно оставить выключенным, если не используете fine-grained authorization)
4. Нажмите **Next**
5. На вкладке **Capability config**:
   - Убедитесь, что включен **Client authentication**
   - Включите **Service accounts roles** (если нужно использовать service accounts)
   - **ВАЖНО:** Включите **Direct access grants** (это необходимо для `grant_type: password`)
6. Нажмите **Next**, затем **Save**
7. На вкладке **Credentials** скопируйте **Client secret** (например: `authentication-service-secret`)
8. Обновите `application.properties` или переменные окружения с этим секретом

**Важно:** 
- Client Secret будет использоваться для получения access token через Client Credentials Grant flow.
- **Direct access grants** должен быть включен, если вы хотите использовать `grant_type: password` для получения токенов от имени пользователя.

### 4. Создание Roles

1. В Realm `authentication-service` перейдите в **Realm roles**
2. Создайте роли:
   - `ROLE_USER`
   - `ROLE_ADMIN`

**Важно:** Если вы используете Service Account для Client (`authentication-service-client`), то после создания ролей нужно:
1. Перейти в **Clients** → `authentication-service-client` → вкладка **Service accounts roles**
2. Назначить созданные роли (`ROLE_USER`, `ROLE_ADMIN`) на Service Account этого клиента
3. Это позволит клиенту получать токены с этими ролями при использовании Client Credentials Grant flow

### 5. Создание тестового пользователя

Для тестирования получения токенов через password grant type нужно создать пользователя в Keycloak:

1. В Realm `authentication-service` перейдите в **Users**
2. Нажмите **Create new user**
3. Заполните:
   - **Username**: `Vlad` (или любое другое имя)
   - **Email**: (опционально)
4. Нажмите **Save**
5. Перейдите на вкладку **Credentials**
6. Нажмите **Set password**
7. Введите пароль (например: `password`)
8. Отключите **Temporary** (чтобы пароль не был временным)
9. Нажмите **Save**
10. Перейдите на вкладку **Role mapping**
11. Нажмите **Assign role**
12. **ВАЖНО:** Выберите роли из секции **Realm roles** (не Client roles!)
13. Выберите роли `ROLE_USER` или `ROLE_ADMIN`
14. Нажмите **Assign**

**Критически важно:** Роли должны быть назначены на **Realm level**, а не на Client level. Это гарантирует, что роли будут в `realm_access` в JWT токене, а не в `resource_access`.

### 6. Настройка User Service Client для интеграции с userService

Для того, чтобы userService мог принимать токены от Keycloak и получать пользователей:

1. В Realm `authentication-service` перейдите в **Clients**
2. Нажмите **Create client**
3. Заполните:
   - **Client ID**: `user-service-client`
   - **Client authentication**: ON
   - **Authorization**: OFF
4. Нажмите **Next**
5. На вкладке **Capability config**:
   - Убедитесь, что включен **Client authentication**
   - Включите **Service accounts roles** (если нужно)
   - **ВАЖНО:** Включите **Direct access grants** (если нужно использовать password grant)
6. Нажмите **Next**, затем **Save**
7. На вкладке **Credentials** скопируйте **Client secret** (например: `user-service-secret`)
8. Настройте userService:
   - Добавьте `spring-boot-starter-oauth2-resource-server` dependency
   - Настройте `application.properties`:
     ```properties
     spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8090/realms/authentication-service
     spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8090/realms/authentication-service/protocol/openid-connect/certs
     ```
   - Настройте SecurityConfig для OAuth2 Resource Server
9. В Docker сети userService должен использовать:
   ```properties
   spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/authentication-service
   spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak:8080/realms/authentication-service/protocol/openid-connect/certs
   ```

**Важно:** 
- UserService должен быть в той же Docker сети (`backend-network`), что и Keycloak
- UserService будет проверять JWT токены, полученные от Keycloak
- Роли из `realm_access` будут доступны через `Authentication.getAuthorities()`

## Конфигурация приложения

### application.properties

```properties
keycloak.server.url=http://localhost:8090
keycloak.realm=authentication-service
keycloak.client.id=authentication-service-client
keycloak.client.secret=authentication-service-secret

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8090/realms/authentication-service
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8090/realms/authentication-service/protocol/openid-connect/certs
```

### Docker Environment Variables

В `docker-compose.yml` уже настроены переменные окружения:

```yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://auth-db:5432/auth_db
  SPRING_DATASOURCE_USERNAME: postgres
  SPRING_DATASOURCE_PASSWORD: postgres
  KEYCLOAK_SERVER_URL: http://keycloak:8080
  KEYCLOAK_REALM: authentication-service
  KEYCLOAK_CLIENT_ID: authentication-service-client
  KEYCLOAK_CLIENT_SECRET: authentication-service-secret
```

**Важно:** Внутри Docker сети используется `http://keycloak:8080`, а не `http://localhost:8090`, так как контейнеры общаются по внутренней сети.

## Использование

### Регистрация пользователя

При регистрации пользователя через `/auth/register`:
1. Пользователь создается в локальной БД (PostgreSQL)
2. Пользователь автоматически создается в Keycloak
3. Роль назначается в Keycloak

### Аутентификация

Сервис поддерживает два способа аутентификации:

1. **Собственные JWT токены** (через `/auth/login`)
2. **Keycloak токены** (через OAuth2 Resource Server)

### Взаимодействие с User Service

User Service может проверять токены от Keycloak через:
- `/auth/validate` - валидация токена (если используется собственный JWT)
- OAuth2 Resource Server конфигурация в User Service (рекомендуется для Keycloak токенов)

**Настройка userService для работы с Keycloak:**

1. UserService должен быть настроен как OAuth2 Resource Server
2. UserService должен быть в той же Docker сети (`backend-network`), что и Keycloak
3. При запросе к userService, клиент должен отправлять токен в заголовке:
   ```
   Authorization: Bearer <keycloak_access_token>
   ```
4. UserService автоматически проверит токен через Keycloak JWKS endpoint
5. Роли из `realm_access` будут доступны через Spring Security `Authentication.getAuthorities()`

**Пример получения пользователей из userService:**

```bash
# Получить токен от Keycloak
TOKEN=$(curl -X POST http://localhost:8090/realms/authentication-service/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=user-service-client" \
  -d "client_secret=user-service-secret" \
  -d "username=vlad" \
  -d "password=password" \
  -d "grant_type=password" | jq -r '.access_token')

# Использовать токен для запроса к userService
curl -X GET http://localhost:8082/api/users \
  -H "Authorization: Bearer $TOKEN"
```

## Тестирование Keycloak API в Postman

### 1. Получение JWKS (JSON Web Key Set)

**Метод:** `GET` (не POST!)

**URL:** 
```
http://localhost:8090/realms/authentication-service/protocol/openid-connect/certs
```

**Headers:** Не требуются

**Body:** Не требуется

**Ответ:** JSON с публичными ключами для проверки JWT токенов

### 2. Получение токена через Password Grant (Direct Access Grants)

**Метод:** `POST`

**URL:**
```
http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
```

**Headers:**
```
Content-Type: application/x-www-form-urlencoded
```

**Body (x-www-form-urlencoded):**
```
client_id: authentication-service-client
client_secret: authentication-service-secret
username: Vlad
password: password
grant_type: password
```

**Важно:** 
- `client_secret` обязателен, если Client authentication включен
- Пользователь должен существовать в Keycloak Realm
- Direct Access Grants должен быть включен в настройках клиента

**Успешный ответ:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ...",
  "token_type": "Bearer",
  "not-before-policy": 0,
  "session_state": "...",
  "scope": "profile email"
}
```

### 3. Получение токена через Client Credentials Grant

**Метод:** `POST`

**URL:**
```
http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
```

**Headers:**
```
Content-Type: application/x-www-form-urlencoded
```

**Body (x-www-form-urlencoded):**
```
client_id: authentication-service-client
client_secret: authentication-service-secret
grant_type: client_credentials
```

**Важно:** 
- Этот метод не требует пользователя
- Токен будет содержать роли, назначенные на Service Account клиента

## Troubleshooting

### Проблема: Пользователь не создается в Keycloak

1. Проверьте, что Keycloak запущен: `docker ps | grep keycloak-innowise`
2. Проверьте логи: `docker logs keycloak-innowise`
3. Убедитесь, что Realm и Client созданы правильно
4. Проверьте Client Secret в конфигурации
5. Проверьте, что Keycloak доступен из контейнера authentication-service: `docker exec authentication-service curl http://keycloak:8080/health`

### Проблема: Ошибка при подключении к Keycloak

1. Проверьте URL Keycloak в `application.properties` или `application-docker.properties`
2. Убедитесь, что Keycloak доступен из контейнера (используйте `http://keycloak:8080` внутри Docker сети)
3. Проверьте network `backend-network` в docker-compose.yml
4. Убедитесь, что оба сервиса (`authentication-service` и `keycloak`) подключены к одной сети
5. Проверьте, что Keycloak полностью запустился: `docker logs keycloak-innowise | grep "started"`

### Проблема: Роли не назначаются

1. Убедитесь, что роли `ROLE_USER` и `ROLE_ADMIN` созданы в Realm (Realm roles, не Client roles)
2. Проверьте, что роли имеют правильные имена (с префиксом `ROLE_`)
3. Убедитесь, что роли назначены на **Realm level**, а не на Client level

### Проблема: Роли находятся в `resource_access` вместо `realm_access` в JWT токене

Это означает, что роли назначены на клиент, а не на realm. Для исправления:

1. Откройте пользователя в Keycloak: **Users** → выберите пользователя
2. Перейдите на вкладку **Role mapping**
3. В секции **Realm roles** нажмите **Assign role**
4. Выберите роли `ROLE_USER` или `ROLE_ADMIN` из **Realm roles** (не из Client roles!)
5. Нажмите **Assign**
6. Если роли были назначены на Client level, удалите их:
   - Разверните секцию **Client roles**
   - Найдите клиент `authentication-service-client`
   - Нажмите на крестик рядом с ролью, чтобы удалить
7. Получите новый токен - теперь роли должны быть в `realm_access`

### Проблема: Ошибка "invalid_client" или "Invalid client credentials" при получении токена

1. Проверьте, что `client_id` указан правильно: `authentication-service-client`
2. Проверьте, что `client_secret` совпадает с секретом в Keycloak (вкладка **Credentials** клиента)
3. Убедитесь, что Client authentication включен в настройках клиента
4. Если используете `grant_type: password`, убедитесь, что **Direct access grants** включен в настройках клиента
5. Проверьте, что Realm называется `authentication-service`
6. Проверьте, что пользователь существует в Keycloak и пароль правильный

### Проблема: Ошибка "405 Method Not Allowed" на `/certs` endpoint

1. `/certs` endpoint поддерживает только **GET** метод, не POST
2. Не отправляйте body в запросе к `/certs`
3. Используйте просто GET запрос: `GET http://localhost:8090/realms/authentication-service/protocol/openid-connect/certs`

### Проблема: userService не может подключиться к Keycloak

1. Убедитесь, что оба сервиса находятся в одной Docker сети `backend-network`
2. Создайте сеть вручную, если она не существует:
   ```bash
   docker network create backend-network
   ```
3. Проверьте, что userService использует `external: true` для сети в docker-compose.yml
4. Проверьте, что Keycloak доступен из userService:
   ```bash
   docker exec user-service curl http://keycloak:8080/health
   ```
5. Убедитесь, что в `application-docker.properties` userService указан правильный URL:
   ```properties
   spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/authentication-service
   ```

## Настройка интеграции с userService - Пошаговая инструкция

### Шаг 1: Создание Docker сети (если еще не создана)

Перед запуском сервисов создайте общую Docker сеть:

```bash
docker network create backend-network
```

**Важно:** Если сеть уже существует, вы получите сообщение об ошибке - это нормально, просто продолжайте.

### Шаг 2: Запуск authentication-service и Keycloak

В директории `authentication-service`:

```bash
docker compose up -d
```

Это запустит:
- `auth-db` (PostgreSQL для authentication-service)
- `keycloak-db` (PostgreSQL для Keycloak)
- `keycloak-innowise` (Keycloak сервер)
- `authentication-service` (ваш сервис аутентификации)

### Шаг 3: Настройка Keycloak Client для userService

1. Откройте Keycloak Admin Console: `http://localhost:8090`
2. Войдите как `admin` / `admin`
3. Выберите Realm `authentication-service`
4. Перейдите в **Clients** → **Create client**
5. Настройте:
   - **Client ID**: `user-service-client`
   - **Client authentication**: ON
   - **Authorization**: OFF
6. Нажмите **Next**
7. На вкладке **Capability config**:
   - Убедитесь, что включен **Client authentication**
   - Включите **Direct access grants** (если нужно использовать password grant)
8. Нажмите **Next**, затем **Save**
9. На вкладке **Credentials** скопируйте **Client secret** (например: `user-service-secret`)

### Шаг 4: Проверка SecurityConfig в userService

Убедитесь, что `SecurityConfig.java` в userService содержит:
- OAuth2 Resource Server конфигурацию
- JwtAuthenticationConverter для извлечения ролей из `realm_access`
- CORS настройки

Пример правильной конфигурации уже добавлен в `SecurityConfig.java`.

### Шаг 5: Проверка application-docker.properties в userService

Убедитесь, что файл содержит:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/authentication-service
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak:8080/realms/authentication-service/protocol/openid-connect/certs
```

### Шаг 6: Обновление docker-compose.yml в userService

Убедитесь, что сеть настроена как external:

```yaml
networks:
  backend-network:
    external: true
```

### Шаг 7: Запуск userService

В директории `userService`:

```bash
docker compose up -d
```

### Шаг 8: Тестирование интеграции

1. **Получите токен от Keycloak:**

```bash
# В Postman или curl
POST http://localhost:8090/realms/authentication-service/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

Body:
client_id=user-service-client
client_secret=user-service-secret
username=vlad
password=password
grant_type=password
```

2. **Используйте токен для запроса к userService:**

```bash
GET http://localhost:8082/api/users
Authorization: Bearer <ваш_токен_из_шага_1>
```

3. **Проверьте логи userService:**

```bash
docker logs user-service
```

Вы должны увидеть успешную аутентификацию и авторизацию.

### Шаг 9: Проверка ролей в userService

В контроллерах userService вы можете использовать роли так:

```java
@GetMapping("/api/users")
public ResponseEntity<List<User>> getUsers(Authentication authentication) {
    // Получить роли из токена
    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
    
    // Проверить роль
    boolean isAdmin = authorities.stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    
    if (isAdmin) {
        // Вернуть всех пользователей
    } else {
        // Вернуть только своих пользователей
    }
}
```

## Настройка для Production

1. Измените `KEYCLOAK_ADMIN_PASSWORD` на безопасный пароль в `docker-compose.yml`
2. Используйте HTTPS для Keycloak (настройте `KC_HTTPS_ENABLED=true` и SSL сертификаты)
3. Настройте правильные CORS настройки в Keycloak Realm
4. Используйте секреты из переменных окружения, а не из файлов
5. Настройте backup для Keycloak БД (`keycloak_db_data` volume)
6. Измените `KEYCLOAK_CLIENT_SECRET` на более безопасный секрет
7. Отключите `start-dev` режим и используйте production режим с настройкой базы данных
8. Настройте health checks и мониторинг

