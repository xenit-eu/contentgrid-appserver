package com.contentgrid.appserver.autoconfigure.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.flags.RequiredEndpointFlag;
import com.contentgrid.appserver.application.model.settings.ApplicationSettings;
import com.contentgrid.appserver.application.model.settings.database.DatabaseSettings;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SchemaName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.autoconfigure.query.engine.JOOQQueryEngineAutoConfiguration;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.exception.PermissionDeniedException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;

/**
 * Verifies that an application configuring {@code settings.database.schema} really reads and writes through
 * that schema, without any of the generated SQL being schema-qualified.
 */
class ApplicationSchemaQueryEngineTest {

    private static final SchemaName SCHEMA = SchemaName.of("V1");

    private static final SimpleAttribute PERSON_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final Entity PERSON = Entity.builder()
            .name(EntityName.of("person"))
            .table(TableName.of("person"))
            .pathSegment(PathSegmentName.of("persons"))
            .linkName(LinkName.of("persons"))
            .attribute(PERSON_NAME)
            .build();

    private static final SimpleAttribute INVOICE_NUMBER = SimpleAttribute.builder()
            .name(AttributeName.of("number"))
            .column(ColumnName.of("number"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .linkName(LinkName.of("invoices"))
            .attribute(INVOICE_NUMBER)
            .build();

    private static final ManyToOneRelation INVOICE_CUSTOMER = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE.getName())
                    .name(RelationName.of("customer"))
                    .pathSegment(PathSegmentName.of("customer"))
                    .linkName(LinkName.of("customer"))
                    .flag(RequiredEndpointFlag.INSTANCE)
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PERSON.getName())
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .linkName(LinkName.of("invoices"))
                    .build())
            .targetReference(ColumnName.of("customer"))
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("default"))
            .settings(ApplicationSettings.builder()
                    .database(DatabaseSettings.builder()
                            .schema(SCHEMA)
                            .build())
                    .build())
            .entity(PERSON)
            .entity(INVOICE)
            .relation(INVOICE_CUSTOMER)
            .build();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory()
                    .setConversionService(new ApplicationConversionService()))
            .withConfiguration(AutoConfigurations.of(ApplicationSchemaDataSourceAutoConfiguration.class,
                    DataSourceAutoConfiguration.class, TransactionAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class, JooqAutoConfiguration.class,
                    JOOQQueryEngineAutoConfiguration.class))
            .withPropertyValues(
                    // Own database, so the schemas of this test do not clash with the other tests
                    "spring.datasource.url=jdbc:tc:postgresql:15:///application_schema",
                    "contentgrid.appserver.query-engine.bootstrap-tables=create"
            )
            .withBean(ApplicationResolver.class, () -> new SingleApplicationResolver(APPLICATION));

    @Test
    void tablesAreBootstrappedInTheConfiguredSchema() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var dataSource = context.getBean(DataSource.class);

            assertThat(tablesIn(dataSource, SCHEMA.getValue())).contains("person", "invoice");
            assertThat(tablesIn(dataSource, "public")).doesNotContain("person", "invoice");
        });
    }

    @Test
    void permissionPredicateAcrossRelationResolvesInTheConfiguredSchema() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var aliceId = EntityId.of(UUID.randomUUID());
            var bobId = EntityId.of(UUID.randomUUID());
            var aliceInvoiceId = EntityId.of(UUID.randomUUID());
            var bobInvoiceId = EntityId.of(UUID.randomUUID());

            var dslContext = context.getBean(DSLContext.class);
            dslContext.insertInto(DSL.table(DSL.name("person")),
                            DSL.field(DSL.name("id"), UUID.class), DSL.field(DSL.name("name"), String.class))
                    .values(aliceId.getValue(), "alice")
                    .values(bobId.getValue(), "bob")
                    .execute();
            dslContext.insertInto(DSL.table(DSL.name("invoice")),
                            DSL.field(DSL.name("id"), UUID.class),
                            DSL.field(DSL.name("number"), String.class),
                            DSL.field(DSL.name("customer"), UUID.class))
                    .values(aliceInvoiceId.getValue(), aliceInvoiceId.toString(), aliceId.getValue())
                    .values(bobInvoiceId.getValue(), bobInvoiceId.toString(), bobId.getValue())
                    .execute();

            // Unqualified statements end up in the configured schema
            assertThat(dslContext.fetchCount(DSL.table(DSL.name(SCHEMA.getValue(), "invoice")),
                    DSL.field(DSL.name("id"), UUID.class).in(aliceInvoiceId.getValue(), bobInvoiceId.getValue())))
                    .isEqualTo(2);

            // Only invoices of alice may be read. The predicate is resolved into an EXISTS(...) subquery on the
            // related person table, which correlates back to the invoice table through an alias.
            ThunkExpression<Boolean> permitReadPredicate = StringComparison.normalizedEqual(
                    SymbolicReference.of(Variable.named("entity"),
                            SymbolicReference.path("customer"), SymbolicReference.path("name")),
                    Scalar.of("alice"));

            var queryEngine = context.getBean(QueryEngine.class);
            assertThat(queryEngine.findById(APPLICATION,
                    EntityRequest.forEntity(INVOICE.getName(), aliceInvoiceId), permitReadPredicate))
                    .isPresent();
            assertThatThrownBy(() -> queryEngine.findById(APPLICATION,
                    EntityRequest.forEntity(INVOICE.getName(), bobInvoiceId), permitReadPredicate))
                    .isInstanceOf(PermissionDeniedException.class);
        });
    }

    private static List<String> tablesIn(DataSource dataSource, String schema) throws SQLException {
        try (var connection = dataSource.getConnection();
                var tables = connection.getMetaData().getTables(null, schema, null, new String[]{"TABLE"})) {
            var result = new ArrayList<String>();
            while (tables.next()) {
                result.add(tables.getString("TABLE_NAME"));
            }
            return result;
        }
    }
}
