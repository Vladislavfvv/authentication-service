package com.innowise.authenticationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.innowise.authenticationservice.model.Role;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeycloakServiceTest {

    private static final String REALM = "test-realm";
    private static final String USERNAME = "user@example.com";
    private static final String USER_ID = "abc123";

    @Mock
    private Keycloak keycloak;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private UserResource userResource;

    @Mock
    private RolesResource rolesResource;

    @Mock
    private RoleResource roleResource;

    @Mock
    private RoleRepresentation roleRepresentation;

    @Mock
    private RoleMappingResource roleMappingResource;

    @Mock
    private RoleScopeResource roleScopeResource;

    private KeycloakService keycloakService;

    @BeforeEach
    void setUp() {
        keycloakService = new KeycloakService(keycloak, REALM);

        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get(anyString())).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(roleRepresentation);
        when(usersResource.get(anyString())).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
    }

    @Nested
    class CreateUserTests {
        @Test
        @DisplayName("createUser returns Keycloak ID when successful")
        void createUserSuccess() {
            Response response = Response.status(201)
                    .location(URI.create("/admin/realms/" + REALM + "/users/" + USER_ID))
                    .build();

            when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
            when(userResource.toRepresentation()).thenReturn(new UserRepresentation());

            String id = keycloakService.createUser(
                    USERNAME,
                    "password",
                    Role.ROLE_USER,
                    "John",
                    "Doe"
            );

            assertEquals(USER_ID, id);
            verify(userResource).update(any(UserRepresentation.class));
            verify(userResource).resetPassword(any());
            verify(roleScopeResource).add(anyList());
        }

        @Test
        @DisplayName("createUser throws when Keycloak returns error status")
        void createUserErrorStatus() {
            Response response = Response.status(400).entity("error").build();
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    keycloakService.createUser(USERNAME, "password", Role.ROLE_USER, "John", "Doe"));

            assertEquals("Failed to create user in Keycloak", ex.getMessage());
        }
    }

    @Nested
    class UpdatePasswordTests {
        @Test
        @DisplayName("updatePassword resets password when user exists")
        void updatePasswordSuccess() {
            UserRepresentation representation = new UserRepresentation();
            representation.setId(USER_ID);
            when(usersResource.search(USERNAME)).thenReturn(Collections.singletonList(representation));

            keycloakService.updatePassword(USERNAME, "newPassword");

            verify(usersResource).get(USER_ID);
        }

        @Test
        @DisplayName("updatePassword does nothing when user not found")
        void updatePasswordUserNotFound() {
            when(usersResource.search(USERNAME)).thenReturn(Collections.emptyList());

            keycloakService.updatePassword(USERNAME, "newPassword");

            verify(usersResource, never()).get(anyString());
        }
    }

    @Nested
    class DeleteUserTests {
        @Test
        @DisplayName("deleteUser removes user when exists")
        void deleteUserSuccess() {
            UserRepresentation representation = new UserRepresentation();
            representation.setId(USER_ID);
            when(usersResource.search(USERNAME)).thenReturn(Collections.singletonList(representation));

            keycloakService.deleteUser(USERNAME);

            verify(usersResource).delete(USER_ID);
        }

        @Test
        @DisplayName("deleteUser does nothing when user missing")
        void deleteUserUserNotFound() {
            when(usersResource.search(USERNAME)).thenReturn(Collections.emptyList());

            keycloakService.deleteUser(USERNAME);

            verify(usersResource, never()).delete(anyString());
        }
    }

    @Nested
    class UserExistsTests {
        @Test
        @DisplayName("userExists returns true when Keycloak returns non-empty list")
        void userExistsTrue() {
            when(usersResource.search(USERNAME)).thenReturn(List.of(new UserRepresentation()));

            assertTrue(keycloakService.userExists(USERNAME));
        }

        @Test
        @DisplayName("userExists returns false when Keycloak returns empty list")
        void userExistsFalse() {
            when(usersResource.search(USERNAME)).thenReturn(Collections.emptyList());

            assertFalse(keycloakService.userExists(USERNAME));
        }
    }
}

