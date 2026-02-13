package com.contentgrid.appserver.autoconfigure.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

@AutoConfiguration
@ConditionalOnClass({Flyway.class, PostgreSQLConfigurationExtension.class})
public class FlywayPostgresAutoConfiguration {

}
