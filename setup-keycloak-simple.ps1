$KEYCLOAK_URL = "http://localhost:8090"
$ADMIN_USER = "admin"
$ADMIN_PASSWORD = "admin"
$REALM_NAME = "authentication-service"

Write-Host "Получение токена администратора..." -ForegroundColor Green
$tokenBody = @{
    username = $ADMIN_USER
    password = $ADMIN_PASSWORD
    grant_type = "password"
    client_id = "admin-cli"
}
$tokenResponse = Invoke-RestMethod -Uri "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" -Method Post -Body $tokenBody -ContentType "application/x-www-form-urlencoded"
$ADMIN_TOKEN = $tokenResponse.access_token

$headers = @{
    "Authorization" = "Bearer $ADMIN_TOKEN"
    "Content-Type" = "application/json"
}

Write-Host "Проверка realm..." -ForegroundColor Green
try {
    Invoke-WebRequest -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME" -Method Get -Headers $headers -UseBasicParsing -ErrorAction Stop | Out-Null
    Write-Host "Realm уже существует" -ForegroundColor Yellow
} catch {
    Write-Host "Создание realm..." -ForegroundColor Green
    $realmBody = @{realm = $REALM_NAME; enabled = $true} | ConvertTo-Json
    Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms" -Method Post -Headers $headers -Body $realmBody | Out-Null
    Write-Host "Realm создан" -ForegroundColor Green
}

Write-Host "Создание ролей..." -ForegroundColor Green
$roles = @("ROLE_USER", "ROLE_ADMIN")
foreach ($role in $roles) {
    try {
        Invoke-WebRequest -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles/$role" -Method Get -Headers $headers -UseBasicParsing -ErrorAction Stop | Out-Null
        Write-Host "Роль $role уже существует" -ForegroundColor Yellow
    } catch {
        $roleBody = @{name = $role} | ConvertTo-Json
        Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/roles" -Method Post -Headers $headers -Body $roleBody | Out-Null
        Write-Host "Роль $role создана" -ForegroundColor Green
    }
}

Write-Host "Создание client user-service-client..." -ForegroundColor Green
$CLIENT_ID = "user-service-client"
$clients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$CLIENT_ID" -Method Get -Headers $headers
if ($clients.Count -eq 0) {
    $clientBody = @{
        clientId = $CLIENT_ID
        enabled = $true
        clientAuthenticatorType = "client-secret"
        publicClient = $false
        directAccessGrantsEnabled = $true
        serviceAccountsEnabled = $false
        standardFlowEnabled = $true
    } | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients" -Method Post -Headers $headers -Body $clientBody | Out-Null
    $createdClients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$CLIENT_ID" -Method Get -Headers $headers
    $clientUuid = $createdClients[0].id
    $clientSecret = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/$clientUuid/client-secret" -Method Get -Headers $headers
    Write-Host "Client создан. Secret: $($clientSecret.value)" -ForegroundColor Cyan
} else {
    Write-Host "Client уже существует" -ForegroundColor Yellow
}

Write-Host "Done!" -ForegroundColor Green

