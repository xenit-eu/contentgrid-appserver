package com.contentgrid.appserver.query.engine.jooq.resolver;

import com.contentgrid.appserver.application.model.Application;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;

/**
 * Resolves the jOOQ {@link DSLContext} to use for an {@link Application}.
 * <p>
 * The database schema configured via {@code ApplicationSettings.database.schema} is applied to generated
 * SQL by explicitly schema-qualifying table references in {@code JOOQUtils.resolveTable(Application, ...)},
 * <em>not</em> through a global jOOQ {@code RenderMapping}. A {@code RenderMapping} that maps the default
 * schema also qualifies unbound, correlated alias.field references (e.g. inside the {@code _allow_read}
 * {@code EXISTS} subquery built by {@code JOOQSymbolicReferenceResolver}), producing invalid SQL such as
 * {@code "schema"."alias"."column"} which PostgreSQL rejects with
 * {@code "invalid reference to FROM-clause entry for table ..."}.
 */
@RequiredArgsConstructor
public class AutowiredDSLContextResolver implements DSLContextResolver {

    private final DSLContext dslContext;

    @Override
    public DSLContext resolve(Application application) {
        return dslContext;
    }
}
