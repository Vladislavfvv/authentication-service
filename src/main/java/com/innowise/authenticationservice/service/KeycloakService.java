package com.innowise.authenticationservice.service;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.innowise.authenticationservice.model.Role;

import java.util.Collections;
import java.util.List;

@Service
@ConditionalOnBean(Keycloak.class)//KeycloakService будет зарегистрирован только если Keycloak доступен
//KeycloakService для интеграции с Keycloak
public class KeycloakService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakService.class);

    private final Keycloak keycloak;//Keycloak клиент для взаимодействия с Keycloak сервером
    private final String realm;//Realm Keycloak

    @Autowired
    public KeycloakService(Keycloak keycloak, @Value("${keycloak.realm:}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    /**
     * Создание пользователя в Keycloak
     */
    public String createUser(String username, String password, Role role, String firstName, String lastName) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmail(username);
        user.setEmailVerified(true);
        user.setRequiredActions(Collections.emptyList());
        user.setFirstName(firstName);
        user.setLastName(lastName);

        String userId = null;
        Response response = usersResource.create(user);
        try {
            int status = response.getStatus();
            if (status >= 300) {
                log.error("Keycloak createUser status: {} {}. Body: {}", status, response.getStatusInfo(), response.readEntity(String.class));
                throw new RuntimeException("Failed to create user in Keycloak");
            }

            log.info("Keycloak createUser status: {}", status);

            userId = getCreatedId(response);

            UserResource userResource = usersResource.get(userId);

            // Синхронизируем профиль, подтверждаем email и убираем обязательные действия
            UserRepresentation kcUser = userResource.toRepresentation();
            kcUser.setEmail(user.getEmail());
            kcUser.setEmailVerified(true);
            kcUser.setEnabled(true);
            kcUser.setRequiredActions(Collections.emptyList());
            kcUser.setFirstName(firstName);
            kcUser.setLastName(lastName);
            userResource.update(kcUser);

            // Установка постоянного пароля
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            userResource.resetPassword(credential);

            // Назначение роли
            RoleRepresentation roleRepresentation = realmResource.roles().get(role.name()).toRepresentation();
            if (roleRepresentation == null) {
                throw new RuntimeException("Role " + role.name() + " not found in Keycloak realm " + realm);
            }
            userResource.roles().realmLevel().add(Collections.singletonList(roleRepresentation));

            UserRepresentation verified = userResource.toRepresentation();
            log.info("Keycloak user {} created. emailVerified={}, requiredActions={}",
                    username,
                    verified.isEmailVerified(),
                    verified.getRequiredActions());
        } finally {
            response.close();
        }

        if (userId == null) {
            throw new IllegalStateException("Keycloak user identifier was not generated");
        }

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
     * Обновление профиля пользователя (логин, имя, фамилия)
     */
    public void updateUserProfile(String currentLogin, String newLogin, String firstName, String lastName) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();

        List<UserRepresentation> users = usersResource.search(currentLogin);
        if (users.isEmpty()) {
            throw new RuntimeException("User " + currentLogin + " not found in Keycloak");
        }

        UserResource userResource = usersResource.get(users.get(0).getId());
        UserRepresentation representation = userResource.toRepresentation();
        representation.setUsername(newLogin);
        representation.setEmail(newLogin);
        representation.setFirstName(firstName);
        representation.setLastName(lastName);
        representation.setEnabled(true);
        userResource.update(representation);
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

