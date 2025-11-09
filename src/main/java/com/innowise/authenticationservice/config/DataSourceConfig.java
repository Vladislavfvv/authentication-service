package com.innowise.authenticationservice.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);
    private static final String POSTGRESQL_CATALOG_MISSING_SQLSTATE = "3D000";

    @Value("${spring.datasource.create-database.admin-db:postgres}")
    private String adminDatabase;

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) throws SQLException {
        ensureDatabaseExists(properties);
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    private void ensureDatabaseExists(DataSourceProperties properties) throws SQLException {
        String url = properties.getUrl();
        if (url == null || url.isBlank()) {
            return;
        }

        try (Connection ignored = DriverManager.getConnection(url, properties.getUsername(), properties.getPassword())) {
            // database exists
            return;
        } catch (SQLException ex) {
            if (!POSTGRESQL_CATALOG_MISSING_SQLSTATE.equals(ex.getSQLState())) {
                throw ex;
            }
            DatabaseConnectionInfo info = parseConnectionInfo(url);
            String adminUrl = info.adminUrl(adminDatabase);
            log.info("Database '{}' not found. Attempting to create it using {}", info.database(), adminUrl);
            try (Connection adminConnection =
                         DriverManager.getConnection(adminUrl, properties.getUsername(), properties.getPassword());
                 Statement statement = adminConnection.createStatement()) {
                statement.execute("CREATE DATABASE \"" + info.database() + "\"");
                log.info("Database '{}' created successfully", info.database());
            }
        }
    }

    private DatabaseConnectionInfo parseConnectionInfo(String url) {
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == url.length() - 1) {
            return new DatabaseConnectionInfo(url, "");
        }

        int paramsIndex = url.indexOf('?', lastSlash);
        String dbName = paramsIndex > 0 ? url.substring(lastSlash + 1, paramsIndex) : url.substring(lastSlash + 1);
        String baseUrl = url.substring(0, lastSlash + 1);
        String params = paramsIndex > 0 ? url.substring(paramsIndex) : "";
        return new DatabaseConnectionInfo(baseUrl, params, dbName);
    }

    private record DatabaseConnectionInfo(String baseUrl, String params, String database) {

        DatabaseConnectionInfo(String originalUrl, String database) {
            this(originalUrl, "", database);
        }

        String adminUrl(String adminDatabase) {
            return baseUrl + adminDatabase + params;
        }
    }
}


