package com.innowise.authenticationservice.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = false)
//name = "keycloak.enabled" - проверяем конфигурационное свойство keycloak.enabled (ищется в application.properties, профилях, переменных окружения и т.д.).
//havingValue = "true" - если значение свойства равно true, то конфигурация будет применена.
//matchIfMissing = false - если свойство не найдено, то конфигурация НЕ будет применена (Keycloak опционален).
//Если keycloak.enabled=false или свойство отсутствует, класс игнорируется: 
//Keycloak-бины не регистрируются, и сервис работает без интеграции
public class KeycloakConfig {
    //URL Keycloak сервера
    @Value("${keycloak.server.url}")
    private String keycloakServerUrl;

    //Realm Keycloak
    @Value("${keycloak.realm}")
    private String realm;

    //Client ID Keycloak
    @Value("${keycloak.client.id}")
    private String clientId;

    //Client Secret Keycloak
    @Value("${keycloak.client.secret}")
    private String clientSecret;

    // Auth type: client (default) or admin
    @Value("${keycloak.auth.type:client}")
    private String authType;

    // Admin credentials (optional)
    @Value("${keycloak.admin.username:}")
    private String adminUsername;

    @Value("${keycloak.admin.password:}")
    private String adminPassword;

    @Value("${keycloak.admin.realm:master}")
    private String adminRealm;

    @Bean
    public Keycloak keycloak() {
        KeycloakBuilder builder = KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl);

        if ("admin".equalsIgnoreCase(authType) && !adminUsername.isEmpty() && !adminPassword.isEmpty()) {
            // Use admin username/password to obtain token via password grant against master realm
            return builder
                    .realm(adminRealm)
                    .clientId("admin-cli")
                    .username(adminUsername)
                    .password(adminPassword)
                    .grantType("password")
                    .build();
        }

        // Default: client credentials (requires service account with realm-management roles)
        return builder
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType("client_credentials")
                .build();
    }
}

