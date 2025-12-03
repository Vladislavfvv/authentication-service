# Управление пользователями в базе данных authentication-service

## Подключение к базе данных


Правильная команда:
docker exec us_db_pg psql -U postgres -d us_db -c "SELECT * FROM userservice_data.users;"
Разница:
Неправильно: us_db (такого контейнера нет)
Правильно: us_db_pg (имя контейнера из docker-compose.yml)
Параметры команды:
us_db_pg — имя контейнера (из container_name в docker-compose.yml)
-U postgres — пользователь
-d us_db — имя базы данных (из POSTGRES_DB)
userservice_data.users — схема и таблица
Другие полезные команды:
Посмотреть все таблицы в схеме:
docker exec us_db_pg psql -U postgres -d us_db -c "\dt userservice_data.*"
Подключиться интерактивно:
docker exec -it us_db_pg psql -U postgres -d us_db
Посмотреть все схемы:
docker exec us_db_pg psql -U postgres -d us_db -c "\dn"
Посмотреть все базы данных:
docker exec us_db_pg psql -U postgres -c "\l"
Данные в базе есть — видно 5 пользователей.


docker exec us_db_pg psql -U postgres -d us_db -c "SELECT id, email, name, surname FROM userservice_data.users ORDER BY id;"


docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role FROM public.users ORDER BY 
id;"


docker exec us_db_pg psql -U postgres -d us_db -c "SELECT user_id FROM userservice_data.card_info WHERE user_id = 4;"

смена ID
docker exec us_db_pg psql -U postgres -d us_db -c "BEGIN; UPDATE userservice_data.card_info SET user_id = 3 WHERE user_id = 4; UPDATE userservice_data.users SET id = 3 WHERE id = 4; SELECT setval('userservice_data.users_id_seq', (SELECT MAX(id) FROM userservice_data.users)); COMMIT;"





```bash
docker exec -it auth_db psql -U postgres -d auth_db
```

Или выполнение одной команды:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "SQL_COMMAND"
```

## ⚠️ ВАЖНО: Правильное имя схемы

В базе данных `auth_db` используется схема **`public`**, а НЕ `auth_data` или `auth_db_data`!

## Просмотр всех пользователей

### Все пользователи:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "SELECT * FROM public.users ORDER BY id;"
```

### Пользователи с основными полями:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role, first_name, last_name, created_at FROM public.users ORDER BY id;"
```

### Количество пользователей:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "SELECT COUNT(*) as total_users FROM public.users;"
```

### Поиск пользователя по login:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role, first_name, last_name FROM public.users WHERE login = 'example@example.com';"
```

### Поиск пользователей по части login:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "SELECT id, login, role, first_name, last_name FROM public.users WHERE login LIKE '%mylogin%' ORDER BY id;"
```

## Структура таблицы users

```sql
CREATE TABLE public.users (
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
```

## Просмотр схем и таблиц

### Список всех схем:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "\dn"
```

### Список всех таблиц в схеме public:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "\dt public.*"
```

### Информация о таблице users:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "\d public.users"
```

## Удаление пользователей

### ⚠️ ВНИМАНИЕ: Удаление пользователя из authentication-service НЕ удаляет его из Keycloak автоматически!

### Удаление пользователя по ID:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "DELETE FROM public.users WHERE id = 5;"
```

### Удаление пользователя по login:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "DELETE FROM public.users WHERE login = 'example@example.com';"
```

### Удаление нескольких пользователей:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "DELETE FROM public.users WHERE id IN (5, 7);"
```

## Полезные SQL запросы

### Просмотр всех пользователей (краткий формат):
```sql
SELECT id, login, role, first_name, last_name, created_at 
FROM public.users 
ORDER BY id;
```

### Поиск пользователей по роли:
```sql
SELECT id, login, role, first_name, last_name 
FROM public.users 
WHERE role = 'ROLE_USER' 
ORDER BY id;
```

### Поиск администраторов:
```sql
SELECT id, login, role, first_name, last_name 
FROM public.users 
WHERE role = 'ROLE_ADMIN' 
ORDER BY id;
```

### Статистика по ролям:
```sql
SELECT role, COUNT(*) as count 
FROM public.users 
GROUP BY role 
ORDER BY count DESC;
```

## Синхронизация с Keycloak

После удаления пользователя из базы данных `auth_db`, его также нужно удалить из Keycloak (если он там есть).

### Проверка пользователя в Keycloak:
```bash
# Используйте скрипт check-user-in-keycloak.ps1
# или через Keycloak Admin Console
```

### Удаление из Keycloak:
```bash
# Используйте Keycloak Admin Console или API
# Или скрипт для удаления пользователя
```

## Резервное копирование

### Создание резервной копии пользователя:
```sql
CREATE TABLE IF NOT EXISTS public.users_backup AS 
SELECT * FROM public.users WHERE id = 5;
```

### Экспорт всех пользователей:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "\copy (SELECT * FROM public.users) TO '/tmp/users_backup.csv' CSV HEADER;"
```

## Восстановление из резервной копии

```sql
INSERT INTO public.users 
SELECT * FROM public.users_backup WHERE id = 5;
```

## Примеры использования

### Найти и удалить тестовых пользователей:
```bash
docker exec auth_db psql -U postgres -d auth_db -c "DELETE FROM public.users WHERE login LIKE '%test%' OR login LIKE '%example.com%';"
```

### Очистка старых пользователей (старше определенной даты):
```sql
DELETE FROM public.users 
WHERE created_at < '2024-01-01';
```

### Обновление роли пользователя:
```sql
UPDATE public.users 
SET role = 'ROLE_ADMIN' 
WHERE login = 'admin@example.com';
```

## Частые ошибки

### ❌ НЕПРАВИЛЬНО:
```bash
# Ошибка: relation "auth_data.users" does not exist
docker exec auth_db psql -U postgres -d auth_db -c "SELECT * FROM auth_data.users;"

# Ошибка: relation "auth_db_data.users" does not exist
docker exec auth_db psql -U postgres -d auth_db -c "SELECT * FROM auth_db_data.users;"
```

### ✅ ПРАВИЛЬНО:
```bash
# Используйте схему public
docker exec auth_db psql -U postgres -d auth_db -c "SELECT * FROM public.users;"

# Или просто users (public - схема по умолчанию)
docker exec auth_db psql -U postgres -d auth_db -c "SELECT * FROM users;"
```

