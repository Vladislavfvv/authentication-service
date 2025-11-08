# Автоматическая настройка Keycloak

## Зачем нужны два скрипта?

1. **`setup-keycloak.ps1`** - PowerShell скрипт для Windows
2. **`keycloak-setup.sh`** - Bash скрипт для Linux/Mac

Оба скрипта делают одно и то же - автоматически настраивают Keycloak, создавая:
- Realm `authentication-service`
- Роли `ROLE_USER` и `ROLE_ADMIN`
- Client `authentication-service-client`
- Client `user-service-client`

## Использование

### Windows (PowerShell):
```powershell
powershell -ExecutionPolicy Bypass -File setup-keycloak.ps1
```

### Linux/Mac (Bash):
```bash
chmod +x keycloak-setup.sh
./keycloak-setup.sh
```

## Требования

- Keycloak должен быть запущен и доступен на `http://localhost:8090`
- Для bash скрипта требуется `jq` (JSON parser): `sudo apt-get install jq` или `brew install jq`

## Что делает скрипт?

1. Ожидает готовности Keycloak
2. Получает токен администратора
3. Создает realm `authentication-service` (если не существует)
4. Создает роли `ROLE_USER` и `ROLE_ADMIN` (если не существуют)
5. Создает clients с правильными настройками
6. Выводит Client Secret для каждого созданного client

## Важно

- Client Secret генерируется автоматически Keycloak при создании client
- Скрипт выводит сгенерированные секреты в консоль
- Сохраните эти секреты для использования в приложении

## Альтернатива

Если скрипты не работают, можно настроить Keycloak вручную через Admin Console:
1. Откройте http://localhost:8090
2. Войдите как `admin` / `admin`
3. Следуйте инструкциям в `KEYCLOAK_SETUP.md`

