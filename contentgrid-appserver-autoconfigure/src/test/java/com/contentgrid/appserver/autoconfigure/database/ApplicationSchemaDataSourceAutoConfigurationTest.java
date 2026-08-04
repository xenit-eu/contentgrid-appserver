package com.contentgrid.appserver.autoconfigure.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.settings.ApplicationSettings;
import com.contentgrid.appserver.application.model.settings.database.DatabaseSettings;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.SchemaName;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApplicationSchemaDataSourceAutoConfigurationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/appserver";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory()
                    .setConversionService(new ApplicationConversionService()))
            .withConfiguration(AutoConfigurations.of(ApplicationSchemaDataSourceAutoConfiguration.class,
                    DataSourceAutoConfiguration.class))
            .withPropertyValues("spring.datasource.url=" + JDBC_URL);

    private ApplicationContextRunner withDatabaseSettings(DatabaseSettings databaseSettings) {
        return contextRunner.withBean(ApplicationResolver.class, () -> new SingleApplicationResolver(
                Application.builder()
                        .name(ApplicationName.of("default"))
                        .settings(ApplicationSettings.builder()
                                .database(databaseSettings)
                                .build())
                        .build()));
    }

    @Test
    void schemaFromApplicationModelIsAppliedToConnections() {
        withDatabaseSettings(DatabaseSettings.builder().schema(SchemaName.of("V1")).build())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DataSource.class);
                    var dataSource = context.getBean(HikariDataSource.class);
                    assertThat(dataSource.getSchema()).isEqualTo("V1");
                    assertThat(dataSource.getJdbcUrl()).isEqualTo(JDBC_URL);
                });
    }

    @Test
    void withoutDatabaseSettings_noSchemaIsApplied() {
        contextRunner
                .withBean(ApplicationResolver.class, () -> new SingleApplicationResolver(Application.builder()
                        .name(ApplicationName.of("default"))
                        .build()))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HikariDataSource.class).getSchema()).isNull();
                });
    }

    @Test
    void publicSchemaIsTheDefaultSchema_soNoSchemaIsApplied() {
        withDatabaseSettings(DatabaseSettings.builder().schema(SchemaName.PUBLIC).build())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HikariDataSource.class).getSchema()).isNull();
                });
    }

    @Test
    void dataSourcePropertiesAreStillApplied() {
        withDatabaseSettings(DatabaseSettings.builder().schema(SchemaName.of("V1")).build())
                .withPropertyValues(
                        "spring.datasource.username=appserver-user",
                        "spring.datasource.name=appserver-pool",
                        "spring.datasource.hikari.maximum-pool-size=7"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var dataSource = context.getBean(HikariDataSource.class);
                    assertThat(dataSource.getSchema()).isEqualTo("V1");
                    assertThat(dataSource.getUsername()).isEqualTo("appserver-user");
                    assertThat(dataSource.getPoolName()).isEqualTo("appserver-pool");
                    assertThat(dataSource.getMaximumPoolSize()).isEqualTo(7);
                });
    }

    @Test
    void connectionDetailsTakePrecedenceOverProperties() {
        withDatabaseSettings(DatabaseSettings.builder().schema(SchemaName.of("V1")).build())
                .withBean(JdbcConnectionDetails.class, () -> new JdbcConnectionDetails() {
                    @Override
                    public String getUsername() {
                        return "connection-details-user";
                    }

                    @Override
                    public String getPassword() {
                        return "connection-details-password";
                    }

                    @Override
                    public String getJdbcUrl() {
                        return "jdbc:postgresql://localhost:5432/from-connection-details";
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var dataSource = context.getBean(HikariDataSource.class);
                    assertThat(dataSource.getSchema()).isEqualTo("V1");
                    assertThat(dataSource.getJdbcUrl())
                            .isEqualTo("jdbc:postgresql://localhost:5432/from-connection-details");
                    assertThat(dataSource.getUsername()).isEqualTo("connection-details-user");
                });
    }

    @Test
    void withoutApplicationResolver_theDefaultDataSourceIsUsed() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataSource.class);
            assertThat(context).doesNotHaveBean(ApplicationSchemaDataSourceAutoConfiguration.class);
        });
    }

    @Test
    void userDefinedDataSourceIsNotReplaced() {
        var userDataSource = new HikariDataSource();
        userDataSource.setJdbcUrl(JDBC_URL);
        withDatabaseSettings(DatabaseSettings.builder().schema(SchemaName.of("V1")).build())
                .withBean("dataSource", DataSource.class, () -> userDataSource)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context.getBean(DataSource.class)).isSameAs(userDataSource);
                    assertThat(userDataSource.getSchema()).isNull();
                });
    }
}
