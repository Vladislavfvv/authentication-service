-- Ensure necessary crypto extension exists before inserting users.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Seed default administrator account for authentication-service.
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

