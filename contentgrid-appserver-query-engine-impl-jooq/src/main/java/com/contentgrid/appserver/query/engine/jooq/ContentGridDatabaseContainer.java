package com.contentgrid.appserver.query.engine.jooq;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;


@Slf4j
@ConditionalOnExpression("!environment.containsProperty('spring.datasource.url')")
@Configuration
public class ContentGridDatabaseContainer {

    @NonNull
    private static final String databaseName = "contentgrid";

    private static final int port = 5432;

    @Bean
    public GenericContainer<?> jooqContainer(@Value("${spring.datasource.username:contentgrid}") String username,
                                             @Value("${spring.datasource.password:contentgrid}") String password) {
        log.warn("The application is starting a temporary database using Testcontainers. This is not recommended for production use. " +
                "Please configure a persistent database connection via 'spring.datasource.url'.");
        ImageFromDockerfile dockerFileImage = new ImageFromDockerfile()
                .withFileFromClasspath("Dockerfile", "com/contentgrid/appserver/query/engine/jooq/docker/Dockerfile");

        return new GenericContainer<>(dockerFileImage)
                .withExposedPorts(port)
                .withEnv("POSTGRES_DB", databaseName)
                .withEnv("POSTGRES_USER", username)
                .withEnv("POSTGRES_PASSWORD", password)
                .withCommand("postgres", "-c", "log_statement=all", "-c", "log_destination=stderr");
    }

    @Bean
    @Primary
    JdbcConnectionDetails jdbcDetails(@NonNull GenericContainer<?> jooqContainer,
                                      @Value("${spring.datasource.username:contentgrid}") String username,
                                      @Value("${spring.datasource.password:contentgrid}") String password) {
        String host = jooqContainer.getHost();
        int mappedPort = jooqContainer.getMappedPort(port);

        return new JdbcConnectionDetails() {
            @Override public String getJdbcUrl() {return "jdbc:postgresql://%s:%d/%s".formatted(host, mappedPort, databaseName); }
            @Override public String getUsername() { return username; }
            @Override public String getPassword() { return password; }
            @Override public String getDriverClassName() { return "org.postgresql.Driver"; }
        };
    }

}
