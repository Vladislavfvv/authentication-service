# Инструкция по запуску через Docker Compose

## Проблемы, которые были исправлены:

1. **Неправильный путь в docker-compose.yml**
   - Было: `build: ./authentication-service`
   - Стало: `build: .`
   - Причина: docker-compose.yml находится в корне проекта, поэтому нужно указывать текущую директорию

2. **Удален user-service из docker-compose**
   - user-service должен быть в отдельной директории и иметь свой docker-compose.yml

3. **Упрощен healthcheck для Keycloak**
   - Используется встроенная команда bash вместо curl

4. **Добавлена H2 база для тестов**
   - Тесты теперь не требуют запущенной PostgreSQL

## Запуск Docker Compose

### Вариант 1: Запуск всех сервисов (включая Keycloak)

```bash
docker compose up --build
```

### Вариант 2: Запуск без Keycloak (только БД и сервис)

```bash
docker compose up --build auth-db authentication-service
```

### Вариант 3: Запуск в фоновом режиме

```bash
docker compose up --build -d
```

## Проверка работы

После запуска проверьте:

1. **База данных auth-db**:
   ```bash
   docker ps | grep auth_db
   ```

2. **Keycloak** (если запущен):
   - Откройте браузер: http://localhost:8090
   - Логин: `admin`
   - Пароль: `admin`

3. **Authentication Service**:
   ```bash
   curl http://localhost:8081/auth/login
   ```

## Остановка сервисов

```bash
docker compose down
```

## Удаление всех данных (volumes)

```bash
docker compose down -v
```

## Логи сервисов

```bash
# Все логи
docker compose logs

# Логи конкретного сервиса
docker compose logs authentication-service
docker compose logs keycloak
docker compose logs auth-db
```

## Решение проблем

### Проблема: Порт уже занят

Если порт 8081, 8090, 5433 уже занят:

1. Остановите другие сервисы, использующие эти порты
2. Или измените порты в docker-compose.yml:
   ```yaml
   ports:
     - "8082:8081"  # Вместо 8081:8081
   ```

### Проблема: Keycloak не запускается

1. Проверьте логи: `docker compose logs keycloak`
2. Убедитесь, что keycloak-db запустилась: `docker compose ps`
3. Дождитесь полной инициализации Keycloak (может занять 1-2 минуты)

### Проблема: Сервис не может подключиться к БД

1. Проверьте, что auth-db запущена: `docker compose ps`
2. Проверьте healthcheck: `docker compose ps` должен показывать "healthy"
3. Проверьте логи: `docker compose logs authentication-service`

## Сборка JAR для Docker

Перед запуском docker compose убедитесь, что проект собран:

```bash
mvn clean package -DskipTests
```

Или Docker сам соберет проект при `docker compose up --build`.

