# PowerShell скрипт для автоматической настройки Keycloak
# Создает realm, client, роли и тестового пользователя

$KEYCLOAK_URL = "http://localhost:8090"
$ADMIN_USER = "admin"
$ADMIN_PASSWORD = "admin"
$REALM_NAME = "authentication-service"
$CLIENT_ID = "authentication-service-client"
$USER_SERVICE_CLIENT_ID = "user-service-client"

Write-Host "Ожидание запуска Keycloak..." -ForegroundColor Yellow

# Ожидание готовности Keycloak (проверяем главную страницу)
do {
    try {
        $response = Invoke-WebRequest -Uri "$KEYCLOAK_URL" -UseBasicParsing -ErrorAction Stop
        $ready = $response.StatusCode -eq 200
    } catch {
        $ready = $false
        Write-Host "Keycloak еще не готов, ждем 5 секунд..." -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    }
} while (-not $ready)

# Дополнительное ожидание для полной инициализации
Write-Host "Ожидание полной инициализации Keycloak (30 секунд)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host "Keycloak готов! Получение токена администратора..." -ForegroundColor Green

# Получение токена администратора
$tokenBody = @{
    username = $ADMIN_USER
    password = $ADMIN_PASSWORD
    grant_type = "password"
    client_id = "admin-cli"
}

try {
    $tokenResponse = Invoke-RestMethod -Uri "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" `
        -Method Post `
        -Body $tokenBody `
        -ContentType "application/x-www-form-urlencoded"
    
    $ADMIN_TOKEN = $tokenResponse.access_token
    
    if (-not $ADMIN_TOKEN) {
        Write-Host "Ошибка: Не удалось получить токен администратора" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Ошибка при получении токена: $_" -ForegroundColor Red
    exit 1
}

Write-Host "Токен получен. Проверка существования realm..." -ForegroundColor Green

$headers = @{
    "Authorization" = "Bearer $ADMIN_TOKEN"
    "Content-Type" = "application/json"
}

# Проверка существования realm
try {
    $realmResponse = Invoke-WebRequest -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME" `
        -Method Get `
        -Headers $headers `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Host "Realm '$REALM_NAME' уже существует, пропускаем создание." -ForegroundColor Yellow
} catch {
    Write-Host "Создание realm '$REALM_NAME'..." -ForegroundColor Green
    
    $realmBody = @{
        realm = $REALM_NAME
        enabled = $true
    } | ConvertTo-Json
    
    try {
        Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms" `
            -Method Post `
            -Headers $headers `
            -Body $realmBody | Out-Null
        Write-Host "Realm создан." -ForegroundColor Green
    } catch {
        Write-Host "Ошибка при создании realm: $_" -ForegroundColor Red
    }
}

# Создание ролей
Write-Host "Создание ролей..." -ForegroundColor Green
$roles = @("ROLE_USER", "ROLE_ADMIN")

foreach ($role in $roles) {
    try {
        $roleResponse = Invoke-WebRequest -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles/$role" `
            -Method Get `
            -Headers $headers `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Host "Роль '$role' уже существует." -ForegroundColor Yellow
    } catch {
        $roleBody = @{
            name = $role
        } | ConvertTo-Json
        
        try {
            Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles" `
                -Method Post `
                -Headers $headers `
                -Body $roleBody | Out-Null
            Write-Host "Роль '$role' создана." -ForegroundColor Green
        } catch {
            Write-Host "Ошибка при создании роли '$role': $_" -ForegroundColor Red
        }
    }
}

# Создание client: authentication-service-client
Write-Host "Создание client '$CLIENT_ID'..." -ForegroundColor Green
try {
    $clients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$CLIENT_ID" `
        -Method Get `
        -Headers $headers
    
    if ($clients.Count -eq 0) {
        $clientBody = @{
            clientId = $CLIENT_ID
            enabled = $true
            clientAuthenticatorType = "client-secret"
            publicClient = $false
            directAccessGrantsEnabled = $true
            serviceAccountsEnabled = $true
            standardFlowEnabled = $true
        } | ConvertTo-Json -Depth 10
        
        Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients" `
            -Method Post `
            -Headers $headers `
            -Body $clientBody | Out-Null
        
        # Получение ID созданного client для получения secret
        $createdClients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$CLIENT_ID" `
            -Method Get `
            -Headers $headers
        $clientUuid = $createdClients[0].id
        
        # Получение secret
        $clientSecret = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/$clientUuid/client-secret" `
            -Method Get `
            -Headers $headers
        
        Write-Host "Client '$CLIENT_ID' создан." -ForegroundColor Green
        Write-Host "  Client Secret: $($clientSecret.value)" -ForegroundColor Cyan
    } else {
        Write-Host "Client '$CLIENT_ID' уже существует." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Ошибка при создании client '$CLIENT_ID': $_" -ForegroundColor Red
}

# Создание client: user-service-client
Write-Host "Создание client '$USER_SERVICE_CLIENT_ID'..." -ForegroundColor Green
try {
    $userClients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$USER_SERVICE_CLIENT_ID" `
        -Method Get `
        -Headers $headers
    
    if ($userClients.Count -eq 0) {
        $userClientBody = @{
            clientId = $USER_SERVICE_CLIENT_ID
            enabled = $true
            clientAuthenticatorType = "client-secret"
            publicClient = $false
            directAccessGrantsEnabled = $true
            serviceAccountsEnabled = $false
            standardFlowEnabled = $true
        } | ConvertTo-Json -Depth 10
        
        Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients" `
            -Method Post `
            -Headers $headers `
            -Body $userClientBody | Out-Null
        
        # Получение ID созданного client для получения secret
        $createdUserClients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$USER_SERVICE_CLIENT_ID" `
            -Method Get `
            -Headers $headers
        $userClientUuid = $createdUserClients[0].id
        
        # Получение secret
        $userClientSecret = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/$userClientUuid/client-secret" `
            -Method Get `
            -Headers $headers
        
        Write-Host "Client '$USER_SERVICE_CLIENT_ID' создан." -ForegroundColor Green
        Write-Host "  Client Secret: $($userClientSecret.value)" -ForegroundColor Cyan
    } else {
        Write-Host "Client '$USER_SERVICE_CLIENT_ID' уже существует." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Ошибка при создании client '$USER_SERVICE_CLIENT_ID': $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "Настройка Keycloak завершена!" -ForegroundColor Green
Write-Host ""
Write-Host "ВАЖНО: Client Secret генерируется автоматически Keycloak" -ForegroundColor Yellow
Write-Host "Для получения Client Secret используйте Admin Console или API" -ForegroundColor Yellow
