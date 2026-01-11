-- Seed default administrator account for authentication-service.
-- Пароль: admin
-- BCrypt hash для пароля "admin" (сила 10)
-- ВАЖНО: Используется BCrypt, а не PostgreSQL crypt, так как приложение использует BCryptPasswordEncoder
-- Устанавливаем ID=1 явно для админа, чтобы он был первым пользователем
INSERT INTO users (id, login, password_hash, role, first_name, last_name, created_at)
VALUES (
    1,
    'admin@tut.by',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ROLE_ADMIN',
    'Admin',
    'User',
    NOW()
)
ON CONFLICT (login) DO NOTHING;

-- Сбрасываем последовательность, чтобы следующий ID был MAX(id) + 1
-- setval с is_called=true означает, что следующий nextval() вернет value + 1
-- Используем COALESCE для случая, когда таблица пуста
SELECT setval('users_id_seq', 
    GREATEST(COALESCE((SELECT MAX(id) FROM users), 0), 1), 
    true);

-- Дополнительно перезапускаем identity колонку для гарантии
-- Это гарантирует, что PostgreSQL будет использовать правильное значение последовательности
ALTER TABLE users ALTER COLUMN id RESTART;
SELECT setval('users_id_seq', 
    GREATEST(COALESCE((SELECT MAX(id) FROM users), 0), 1), 
    true);

