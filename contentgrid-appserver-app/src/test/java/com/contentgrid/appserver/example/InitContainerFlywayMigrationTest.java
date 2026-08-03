package com.contentgrid.appserver.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@SpringBootTest
@ActiveProfiles({"bootRun", "initContainer"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///?TC_INITSCRIPT=testcontainers/drop-public-schema.sql",
        // Tables must be created by migrations
        "contentgrid.appserver.query-engine.bootstrap-tables=none",
        "contentgrid.appserver.blueprint-artifact.location=classpath:blueprint-artifact",
})
class InitContainerFlywayMigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    DataSource dataSource;

    @Test
    void testMigrationsOnFreshlyProvisionedDatabase() {
        assertThat(flyway.info().applied())
                .filteredOn(migration -> migration.getVersion() != null) // skip "<< Flyway Schema Creation >>"
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly("1.0", "1.1", "1.2", "1.3");

    }

    @Test
    void testSchemasAreCreatedCorrectly() throws SQLException {
        // Schema public is created and owned by the user we use for the connection
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT pg_get_userbyid(nspowner) = current_user AS owned_by_application_user
                        FROM pg_namespace WHERE nspname = 'public'
                        """)) {
            assertThat(result.next()).as("`public` schema was recreated").isTrue();
            assertThat(result.getBoolean("owned_by_application_user")).isTrue();

            // Tables go in public, views go in V1, V2, ...
            var metadata = connection.getMetaData();
            var personAsTable = metadata.getTables(null, null, "person", new String[]{"TABLE"});
            var personAsView = metadata.getTables(null, null, "person", new String[]{"VIEW"});
            assertThat(collectSchemas(personAsTable)).containsExactly("public");
            assertThat(collectSchemas(personAsView)).containsExactly("V1");
        }
    }

    private static List<String> collectSchemas(ResultSet result) throws SQLException {
        var schemas = new ArrayList<String>();
        while (result.next()) {
            schemas.add(result.getString("TABLE_SCHEM"));
        }
        return schemas;
    }
}
