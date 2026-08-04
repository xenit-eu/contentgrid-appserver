package com.contentgrid.appserver.autoconfigure.database;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.autoconfigure.json.schema.ApplicationResolverAutoConfiguration;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Provides the {@link DataSource} that {@link DataSourceAutoConfiguration} would otherwise create, with the
 * database schema configured in the application model ({@code settings.database.schema}) as the current schema
 * of every connection.
 * <p>
 * Applying the schema on the connection (postgres resolves unqualified table names through its
 * {@code search_path}) keeps it out of the generated SQL entirely. Letting jOOQ render the schema instead is
 * not an option: a {@code RenderMapping} for the default schema also qualifies correlated {@code alias.column}
 * references, which postgres rejects with {@code invalid reference to FROM-clause entry for table ...}.
 */
@AutoConfiguration(after = ApplicationResolverAutoConfiguration.class, before = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, HikariDataSource.class, Application.class, ApplicationResolver.class})
@ConditionalOnBean(ApplicationResolver.class)
@EnableConfigurationProperties(DataSourceProperties.class)
@Slf4j
public class ApplicationSchemaDataSourceAutoConfiguration {

    private static final ApplicationName APPLICATION_NAME = ApplicationName.of("default");

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties,
            ObjectProvider<JdbcConnectionDetails> connectionDetails, ApplicationResolver applicationResolver) {
        var dataSource = createDataSource(properties, connectionDetails.getIfAvailable());
        if (StringUtils.hasText(properties.getName())) {
            dataSource.setPoolName(properties.getName());
        }
        ApplicationDatabaseSchema.of(applicationResolver.resolve(APPLICATION_NAME)).ifPresent(schema -> {
            log.info("Using database schema '{}' from the application model", schema);
            // Hikari sets this schema on every connection it creates. The postgres driver does that with
            // 'SET SESSION search_path TO <schema>', which keeps the case of the schema name intact.
            dataSource.setSchema(schema.getValue());
        });
        return dataSource;
    }

    private static HikariDataSource createDataSource(DataSourceProperties properties,
            JdbcConnectionDetails connectionDetails) {
        if (connectionDetails == null) {
            return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        }
        // Connection details contributed by another bean (a testcontainers @ServiceConnection, docker compose,
        // ...) take precedence over the spring.datasource.* properties, as in DataSourceAutoConfiguration.
        return DataSourceBuilder.create(properties.getClassLoader())
                .type(HikariDataSource.class)
                .driverClassName(connectionDetails.getDriverClassName())
                .url(connectionDetails.getJdbcUrl())
                .username(connectionDetails.getUsername())
                .password(connectionDetails.getPassword())
                .build();
    }
}
