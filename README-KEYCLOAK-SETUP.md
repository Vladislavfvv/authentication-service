# Настройка Keycloak

## Автоматическая настройка через JSON импорт

Keycloak настраивается **автоматически** при первом запуске через импорт realm из файла:
- `keycloak/import/authentication-service-realm.json`

При запуске `docker compose up` Keycloak автоматически импортирует:
- Realm `authentication-service`
- Роли `ROLE_USER` и `ROLE_ADMIN`
- Client `authentication-service-client` (с service account)
- Client `user-service-client`
- Администратора `admin@tut.by` с ролью `ROLE_ADMIN`

## Назначение Service Account ролей

**Важно**: После первого запуска Keycloak необходимо назначить service account роли для `authentication-service-client`, так как Keycloak не поддерживает назначение этих ролей через JSON импорт.

### Способ 1: Python скрипт (рекомендуется)

```bash
python setup-service-account-roles.py
```

### Способ 2: PowerShell скрипт

```powershell
powershell -ExecutionPolicy Bypass -File .\setup-service-account-roles.ps1
```

### Способ 3: Вручную через Admin Console

1. Откройте http://localhost:8090/admin
2. Войдите как `admin` / `admin`
3. Выберите realm `authentication-service`
4. Перейдите: **Clients** → `authentication-service-client` → **Service account roles**
5. Нажмите **Assign role** → **Filter by clients** → выберите `realm-management`
6. Выберите роли: `manage-users`, `view-users`, `manage-realm`
7. Нажмите **Assign**

После назначения ролей **перезапустите** `authentication-service`:
```bash
docker compose restart authentication-service
```

## Требования

- Keycloak должен быть запущен и доступен на `http://localhost:8090`
- Для Python скрипта требуется библиотека `requests`: `pip install requests`

## Подробная документация

Для детальной информации о настройке Keycloak см.:
- `STARTUP_INSTRUCTIONS.md` - пошаговая инструкция по запуску
- `QUICK_START.md` - быстрый старт
- `KEYCLOAK_SETUP.md` - детальная настройка Keycloak

