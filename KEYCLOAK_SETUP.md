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
docker-compose up -d keycloak
```

Keycloak будет доступен на `http://localhost:8090`

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
   - **Client authentication**: ON
   - **Authorization**: ON (опционально)
4. Сохраните
5. На вкладке **Credentials** скопируйте **Client secret** (например: `authentication-service-secret`)
6. Обновите `application.properties` или переменные окружения с этим секретом

### 4. Создание Roles

1. В Realm `authentication-service` перейдите в **Realm roles**
2. Создайте роли:
   - `ROLE_USER`
   - `ROLE_ADMIN`

### 5. Настройка User Service Client

1. Создайте еще один Client для User Service:
   - **Client ID**: `user-service-client`
   - **Client secret**: `user-service-secret`
2. Настройте User Service аналогично

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
  KEYCLOAK_SERVER_URL: http://keycloak:8080
  KEYCLOAK_REALM: authentication-service
  KEYCLOAK_CLIENT_ID: authentication-service-client
  KEYCLOAK_CLIENT_SECRET: authentication-service-secret
```

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
- `/auth/validate` - валидация токена
- OAuth2 Resource Server конфигурация в User Service

## Troubleshooting

### Проблема: Пользователь не создается в Keycloak

1. Проверьте, что Keycloak запущен: `docker ps | grep keycloak`
2. Проверьте логи: `docker logs keycloak`
3. Убедитесь, что Realm и Client созданы правильно
4. Проверьте Client Secret в конфигурации

### Проблема: Ошибка при подключении к Keycloak

1. Проверьте URL Keycloak в `application.properties`
2. Убедитесь, что Keycloak доступен из контейнера
3. Проверьте network в docker-compose.yml

### Проблема: Роли не назначаются

1. Убедитесь, что роли `ROLE_USER` и `ROLE_ADMIN` созданы в Realm
2. Проверьте, что роли имеют правильные имена (с префиксом `ROLE_`)

## Настройка для Production

1. Измените `KEYCLOAK_ADMIN_PASSWORD` на безопасный пароль
2. Используйте HTTPS для Keycloak
3. Настройте правильные CORS настройки
4. Используйте секреты из переменных окружения, а не из файлов
5. Настройте backup для Keycloak БД

