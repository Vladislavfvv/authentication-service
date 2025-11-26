#!/usr/bin/env python3
"""
Script to assign service account roles for authentication-service-client
Required roles: manage-users, view-users, manage-realm from realm-management client
"""

import requests
import json
import sys

KEYCLOAK_URL = "http://localhost:8090"
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin"
REALM_NAME = "authentication-service"
CLIENT_ID = "authentication-service-client"

print("=== Setting up Service Account roles for", CLIENT_ID, "===")

# Get admin token
print("Getting admin token...")
token_url = f"{KEYCLOAK_URL}/realms/master/protocol/openid-connect/token"
token_data = {
    "username": ADMIN_USER,
    "password": ADMIN_PASSWORD,
    "grant_type": "password",
    "client_id": "admin-cli"
}

try:
    token_response = requests.post(token_url, data=token_data)
    token_response.raise_for_status()
    admin_token = token_response.json()["access_token"]
    print("Token obtained")
except Exception as e:
    print(f"Error getting token: {e}")
    sys.exit(1)

headers = {
    "Authorization": f"Bearer {admin_token}",
    "Content-Type": "application/json"
}

# Find client
print(f"Finding client {CLIENT_ID}...")
try:
    clients_url = f"{KEYCLOAK_URL}/admin/realms/{REALM_NAME}/clients?clientId={CLIENT_ID}"
    clients_response = requests.get(clients_url, headers=headers)
    clients_response.raise_for_status()
    clients = clients_response.json()
    
    if len(clients) == 0:
        print(f"Client {CLIENT_ID} not found!")
        sys.exit(1)
    
    client_uuid = clients[0]["id"]
    print(f"Client found (UUID: {client_uuid})")
except Exception as e:
    print(f"Error finding client: {e}")
    sys.exit(1)

# Get service account user
print("Getting service account user...")
try:
    service_account_url = f"{KEYCLOAK_URL}/admin/realms/{REALM_NAME}/clients/{client_uuid}/service-account-user"
    service_account_response = requests.get(service_account_url, headers=headers)
    service_account_response.raise_for_status()
    service_account_user = service_account_response.json()
    service_account_user_id = service_account_user["id"]
    print(f"Service account user found (ID: {service_account_user_id})")
except Exception as e:
    print(f"Error getting service account user: {e}")
    print("Make sure serviceAccountsEnabled is true for the client.")
    sys.exit(1)

# Find realm-management client
print("Finding realm-management client...")
try:
    realm_mgmt_url = f"{KEYCLOAK_URL}/admin/realms/{REALM_NAME}/clients?clientId=realm-management"
    realm_mgmt_response = requests.get(realm_mgmt_url, headers=headers)
    realm_mgmt_response.raise_for_status()
    realm_mgmt_clients = realm_mgmt_response.json()
    
    if len(realm_mgmt_clients) == 0:
        print("realm-management client not found!")
        sys.exit(1)
    
    realm_management_client_uuid = realm_mgmt_clients[0]["id"]
    print(f"realm-management client found (UUID: {realm_management_client_uuid})")
except Exception as e:
    print(f"Error finding realm-management client: {e}")
    sys.exit(1)

# Get roles from realm-management client
print("Getting roles from realm-management client...")
required_roles = ["manage-users", "view-users", "manage-realm"]
roles_to_assign = []

for role_name in required_roles:
    try:
        role_url = f"{KEYCLOAK_URL}/admin/realms/{REALM_NAME}/clients/{realm_management_client_uuid}/roles/{role_name}"
        role_response = requests.get(role_url, headers=headers)
        role_response.raise_for_status()
        role = role_response.json()
        roles_to_assign.append(role)
        print(f"Role {role_name} found")
    except Exception as e:
        print(f"Role {role_name} not found: {e}")

if len(roles_to_assign) == 0:
    print("No required roles found!")
    sys.exit(1)

# Assign roles to service account user
print("Assigning roles to service account user...")
try:
    assign_roles_url = f"{KEYCLOAK_URL}/admin/realms/{REALM_NAME}/users/{service_account_user_id}/role-mappings/clients/{realm_management_client_uuid}"
    assign_response = requests.post(assign_roles_url, headers=headers, json=roles_to_assign)
    assign_response.raise_for_status()
    print("Roles successfully assigned!")
except Exception as e:
    print(f"Error assigning roles: {e}")
    print("Please assign roles manually via Keycloak Admin Console:")
    print("  1. Open http://localhost:8090/admin")
    print("  2. Login as admin/admin")
    print("  3. Select realm authentication-service")
    print("  4. Clients -> authentication-service-client -> Service account roles")
    print("  5. Assign role -> Filter by clients -> realm-management")
    print("  6. Select: manage-users, view-users, manage-realm")
    sys.exit(1)

print("=== Done! ===")
print(f"Service account for client {CLIENT_ID} now has required roles for user management")

