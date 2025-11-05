package com.innowise.authenticationservice.service;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.innowise.authenticationservice.model.Role;

import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnBean(Keycloak.class)
public class KeycloakService {

    private final Keycloak keycloak;
    private final String realm;

    @Autowired
    public KeycloakService(Keycloak keycloak, @Value("${keycloak.realm:}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    /**
     * Создание пользователя в Keycloak
     */
    public String createUser(String username, String password, Role role) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmail(username + "@example.com");

        // Создание пользователя
        Response response = usersResource.create(user);
        String userId = getCreatedId(response);

        // Установка пароля
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        usersResource.get(userId).resetPassword(credential);

        // Назначение роли
        RoleRepresentation roleRepresentation = realmResource.roles().get(role.name()).toRepresentation();
        usersResource.get(userId).roles().realmLevel().add(Collections.singletonList(roleRepresentation));

        return userId;
    }

    /**
     * Обновление пароля пользователя в Keycloak
     */
    public void updatePassword(String username, String password) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();
        
        List<UserRepresentation> users = usersResource.search(username);
        if (!users.isEmpty()) {
            String userId = users.get(0).getId();
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            usersResource.get(userId).resetPassword(credential);
        }
    }

    /**
     * Удаление пользователя из Keycloak
     */
    public void deleteUser(String username) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();
        
        List<UserRepresentation> users = usersResource.search(username);
        if (!users.isEmpty()) {
            usersResource.delete(users.get(0).getId());
        }
    }

    /**
     * Проверка существования пользователя в Keycloak
     */
    public boolean userExists(String username) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();
        List<UserRepresentation> users = usersResource.search(username);
        return !users.isEmpty();
    }

    private String getCreatedId(Response response) {
        String location = response.getLocation().getPath();
        return location.substring(location.lastIndexOf('/') + 1);
    }
}

