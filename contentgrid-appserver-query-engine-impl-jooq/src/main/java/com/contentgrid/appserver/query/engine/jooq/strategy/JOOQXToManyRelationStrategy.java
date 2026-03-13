package com.contentgrid.appserver.query.engine.jooq.strategy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.query.engine.jooq.ExceptionUtils;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public abstract sealed class JOOQXToManyRelationStrategy<R extends Relation> implements JOOQRelationStrategy<R>
        permits JOOQOneToManyRelationStrategy, JOOQManyToManyRelationStrategy {

    protected static <X extends Exception> void checkModifiedItems(Collection<UUID> requested, Collection<UUID> actual, Function<EntityId, X> exceptionCreator) throws X {
        var notModified = new HashSet<>(requested);
        notModified.removeAll(actual);

        var maybeException = ExceptionUtils.createMultiple(notModified, entityId -> exceptionCreator.apply(EntityId.of(entityId)));
        if(maybeException.isPresent()) {
            throw maybeException.get();
        }
    }

    @Override
    public boolean isLinked(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity, EntityIdentity targetIdentity) {
        var table = getTable(application, relation);
        var sourceRef = getSourceRef(application, relation);
        var targetRef = getTargetRef(application, relation);

        return dslContext.fetchExists(DSL.selectOne().from(table)
                .where(DSL.and(sourceRef.eq(sourceIdentity.getEntityId().getValue()), targetRef.eq(targetIdentity.getEntityId().getValue()))));
    }

    public abstract void add(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity, Set<EntityIdentity> targetIdentities);

    public abstract void remove(DSLContext dslContext, Application application, R relation, EntityIdentity sourceIdentity, Set<EntityIdentity> targetIdentities);
}
