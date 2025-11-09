package com.innowise.authenticationservice.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
//name = "keycloak.enabled" - проверяем конфигурационное свойство keycloak.enabled (ищется в application.properties, профилях, переменных окружения и т.д.).
//havingValue = "true" - если значение свойства равно true, то конфигурация будет применена.
//matchIfMissing = true - если свойство не найдено, то конфигурация будет применена.
//Если keycloak.enabled=false, класс игнорируется: 
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

    @Bean
    public Keycloak keycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType("client_credentials")
                .build();
    }
}

