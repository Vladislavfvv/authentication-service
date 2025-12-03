#!/bin/bash

# Скрипт для автоматической настройки Keycloak
# Создает realm, client, роли и тестового пользователя

KEYCLOAK_URL="http://localhost:8090"
ADMIN_USER="admin"
ADMIN_PASSWORD="admin"
REALM_NAME="authentication-service"
CLIENT_ID="authentication-service-client"
USER_SERVICE_CLIENT_ID="user-service-client"

echo "Ожидание запуска Keycloak..."
until curl -s -f "${KEYCLOAK_URL}" > /dev/null; do
  echo "Keycloak еще не готов, ждем 5 секунд..."
  sleep 5
done

# Дополнительное ожидание для полной инициализации
echo "Ожидание полной инициализации Keycloak (30 секунд)..."
sleep 30

echo "Keycloak готов! Получение токена администратора..."

# Получение токена администратора
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASSWORD}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [ "$ADMIN_TOKEN" == "null" ] || [ -z "$ADMIN_TOKEN" ]; then
  echo "Ошибка: Не удалось получить токен администратора"
  exit 1
fi

echo "Токен получен. Проверка существования realm..."

# Проверка существования realm
REALM_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")

if [ "$REALM_EXISTS" == "200" ]; then
  echo "Realm '${REALM_NAME}' уже существует, пропускаем создание."
else
  echo "Создание realm '${REALM_NAME}'..."
  
  # Создание realm
  curl -s -X POST "${KEYCLOAK_URL}/admin/realms" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"realm\": \"${REALM_NAME}\",
      \"enabled\": true
    }"
  
  echo "Realm создан."
fi

# Создание ролей
echo "Создание ролей..."
for ROLE in "ROLE_USER" "ROLE_ADMIN"; do
  ROLE_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/roles/${ROLE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")
  
  if [ "$ROLE_EXISTS" != "200" ]; then
    curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/roles" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "{\"name\": \"${ROLE}\"}"
    echo "Роль '${ROLE}' создана."
  else
    echo "Роль '${ROLE}' уже существует."
  fi
done

# Создание client: authentication-service-client
echo "Создание client '${CLIENT_ID}'..."
CLIENT_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${CLIENT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")

if [ "$CLIENT_EXISTS" == "200" ]; then
  CLIENT_LIST=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${CLIENT_ID}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")
  CLIENT_COUNT=$(echo "$CLIENT_LIST" | jq '. | length')
  
  if [ "$CLIENT_COUNT" == "0" ]; then
    curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "{
        \"clientId\": \"${CLIENT_ID}\",
        \"enabled\": true,
        \"clientAuthenticatorType\": \"client-secret\",
        \"publicClient\": false,
        \"directAccessGrantsEnabled\": true,
        \"serviceAccountsEnabled\": true,
        \"standardFlowEnabled\": true
      }"
    
    # Получение ID созданного client для получения secret
    CREATED_CLIENT_LIST=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${CLIENT_ID}" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}")
    CLIENT_UUID=$(echo "$CREATED_CLIENT_LIST" | jq -r '.[0].id')
    
    # Получение secret
    CLIENT_SECRET=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients/${CLIENT_UUID}/client-secret" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r '.value')
    
    echo "Client '${CLIENT_ID}' создан."
    echo "  Client Secret: ${CLIENT_SECRET}"
  else
    echo "Client '${CLIENT_ID}' уже существует."
  fi
else
  echo "Ошибка при проверке client '${CLIENT_ID}'"
fi

# Создание client: user-service-client
echo "Создание client '${USER_SERVICE_CLIENT_ID}'..."
USER_CLIENT_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${USER_SERVICE_CLIENT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")

if [ "$USER_CLIENT_EXISTS" == "200" ]; then
  USER_CLIENT_LIST=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${USER_SERVICE_CLIENT_ID}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")
  USER_CLIENT_COUNT=$(echo "$USER_CLIENT_LIST" | jq '. | length')
  
  if [ "$USER_CLIENT_COUNT" == "0" ]; then
    curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "{
        \"clientId\": \"${USER_SERVICE_CLIENT_ID}\",
        \"enabled\": true,
        \"clientAuthenticatorType\": \"client-secret\",
        \"publicClient\": false,
        \"directAccessGrantsEnabled\": true,
        \"serviceAccountsEnabled\": false,
        \"standardFlowEnabled\": true
      }"
    
    # Получение ID созданного client для получения secret
    CREATED_USER_CLIENT_LIST=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${USER_SERVICE_CLIENT_ID}" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}")
    USER_CLIENT_UUID=$(echo "$CREATED_USER_CLIENT_LIST" | jq -r '.[0].id')
    
    # Получение secret
    USER_CLIENT_SECRET=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients/${USER_CLIENT_UUID}/client-secret" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r '.value')
    
    echo "Client '${USER_SERVICE_CLIENT_ID}' создан."
    echo "  Client Secret: ${USER_CLIENT_SECRET}"
  else
    echo "Client '${USER_SERVICE_CLIENT_ID}' уже существует."
  fi
else
  echo "Ошибка при проверке client '${USER_SERVICE_CLIENT_ID}'"
fi

echo "Настройка Keycloak завершена!"
echo ""
echo "Для получения Client Secret для '${USER_SERVICE_CLIENT_ID}' выполните:"
echo "curl -X GET \"${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${USER_SERVICE_CLIENT_ID}\" \\"
echo "  -H \"Authorization: Bearer \$(curl -s -X POST \"${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token\" -H \"Content-Type: application/x-www-form-urlencoded\" -d \"username=${ADMIN_USER}\" -d \"password=${ADMIN_PASSWORD}\" -d \"grant_type=password\" -d \"client_id=admin-cli\" | jq -r '.access_token')\" | jq '.[0].secret'"

