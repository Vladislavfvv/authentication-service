# Script to assign service account roles for authentication-service-client
# Required roles: manage-users, view-users, manage-realm from realm-management client

$KEYCLOAK_URL = "http://localhost:8090"
$ADMIN_USER = "admin"
$ADMIN_PASSWORD = "admin"
$REALM_NAME = "authentication-service"
$CLIENT_ID = "authentication-service-client"

Write-Host "=== Setting up Service Account roles for $CLIENT_ID ===" -ForegroundColor Cyan

# Get admin token
Write-Host "Getting admin token..." -ForegroundColor Green
$tokenBody = @{
    username = $ADMIN_USER
    password = $ADMIN_PASSWORD
    grant_type = "password"
    client_id = "admin-cli"
}

try {
    $tokenResponse = Invoke-RestMethod -Uri "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" -Method Post -Body $tokenBody -ContentType "application/x-www-form-urlencoded"
    $ADMIN_TOKEN = $tokenResponse.access_token
    Write-Host "Token obtained" -ForegroundColor Green
} catch {
    Write-Host "Error getting token: $_" -ForegroundColor Red
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $ADMIN_TOKEN"
    "Content-Type" = "application/json"
}

# Find client
Write-Host "Finding client $CLIENT_ID..." -ForegroundColor Green
try {
    $clients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$CLIENT_ID" -Method Get -Headers $headers
    if ($clients.Count -eq 0) {
        Write-Host "Client $CLIENT_ID not found!" -ForegroundColor Red
        exit 1
    }
    $clientUuid = $clients[0].id
    Write-Host "Client found (UUID: $clientUuid)" -ForegroundColor Green
} catch {
    Write-Host "Error finding client: $_" -ForegroundColor Red
    exit 1
}

# Get service account user
Write-Host "Getting service account user..." -ForegroundColor Green
try {
    $serviceAccountUser = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/$clientUuid/service-account-user" -Method Get -Headers $headers
    $serviceAccountUserId = $serviceAccountUser.id
    Write-Host "Service account user found (ID: $serviceAccountUserId)" -ForegroundColor Green
} catch {
    Write-Host "Error getting service account user. Make sure serviceAccountsEnabled is true for the client." -ForegroundColor Red
    exit 1
}

# Find realm-management client
Write-Host "Finding realm-management client..." -ForegroundColor Green
try {
    $realmManagementClients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=realm-management" -Method Get -Headers $headers
    if ($realmManagementClients.Count -eq 0) {
        Write-Host "realm-management client not found!" -ForegroundColor Red
        exit 1
    }
    $realmManagementClientUuid = $realmManagementClients[0].id
    Write-Host "realm-management client found (UUID: $realmManagementClientUuid)" -ForegroundColor Green
} catch {
    Write-Host "Error finding realm-management client: $_" -ForegroundColor Red
    exit 1
}

# Get roles from realm-management client
Write-Host "Getting roles from realm-management client..." -ForegroundColor Green
$requiredRoles = @("manage-users", "view-users", "manage-realm")
$rolesToAssign = @()

foreach ($roleName in $requiredRoles) {
    try {
        $role = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/$realmManagementClientUuid/roles/$roleName" -Method Get -Headers $headers
        $rolesToAssign += $role
        Write-Host "Role $roleName found" -ForegroundColor Green
    } catch {
        Write-Host "Role $roleName not found!" -ForegroundColor Yellow
    }
}

if ($rolesToAssign.Count -eq 0) {
    Write-Host "No required roles found!" -ForegroundColor Red
    exit 1
}

# Assign roles to service account user
Write-Host "Assigning roles to service account user..." -ForegroundColor Green
try {
    $rolesBody = $rolesToAssign | ConvertTo-Json -Depth 10
    
    Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/$REALM_NAME/users/$serviceAccountUserId/role-mappings/clients/$realmManagementClientUuid" -Method Post -Headers $headers -Body $rolesBody | Out-Null
    Write-Host "Roles successfully assigned!" -ForegroundColor Green
} catch {
    Write-Host "Error assigning roles: $_" -ForegroundColor Red
    Write-Host "Please assign roles manually via Keycloak Admin Console:" -ForegroundColor Yellow
    Write-Host "  1. Open http://localhost:8090/admin" -ForegroundColor Yellow
    Write-Host "  2. Login as admin/admin" -ForegroundColor Yellow
    Write-Host "  3. Select realm authentication-service" -ForegroundColor Yellow
    Write-Host "  4. Clients -> authentication-service-client -> Service account roles" -ForegroundColor Yellow
    Write-Host "  5. Assign role -> Filter by clients -> realm-management" -ForegroundColor Yellow
    Write-Host "  6. Select: manage-users, view-users, manage-realm" -ForegroundColor Yellow
    exit 1
}

Write-Host "=== Done! ===" -ForegroundColor Green
Write-Host "Service account for client $CLIENT_ID now has required roles for user management" -ForegroundColor Cyan

