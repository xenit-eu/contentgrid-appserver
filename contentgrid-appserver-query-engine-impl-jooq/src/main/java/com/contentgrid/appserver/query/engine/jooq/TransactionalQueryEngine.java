package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.ItemCount;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.query.engine.api.EntityIdAndVersion;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.UpdateResult;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.QueryPageData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
public class TransactionalQueryEngine implements QueryEngine {
    @NonNull
    private final QueryEngine delegate;

    @NonNull
    private final PlatformTransactionManager transactionManager;

    private <T> T runInReadOnlyTransaction(Supplier<T> callable) {
        var tpl = new TransactionTemplate(transactionManager);
        tpl.setReadOnly(true);

        return tpl.execute(tx -> callable.get());
    }

    private <T> T runInWriteTransaction(Supplier<T> callable) {
        var tpl = new TransactionTemplate(transactionManager);

        return tpl.execute(tx -> {
            var savepoint = tx.createSavepoint();
            var hasThrown = true;
            try {
                var ret = callable.get();
                hasThrown = false;
                return ret;
            } finally {
                if(hasThrown) {
                    tx.rollbackToSavepoint(savepoint);
                }
            }
        });
    }

    @Override
    public SliceData findAll(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression, SortData sortData, @NonNull QueryPageData page)
            throws QueryEngineException {
        return runInReadOnlyTransaction(() ->
                delegate.findAll(application, entity, expression, sortData, page)
        );
    }

    @Override
    public Optional<EntityData> findById(@NonNull Application application, @NonNull EntityRequest entityRequest,
            @NonNull ThunkExpression<Boolean> permitReadPredicate) throws QueryEngineException {
        return runInReadOnlyTransaction(() ->
                delegate.findById(application, entityRequest, permitReadPredicate)
        );
    }

    @Override
    public EntityData create(@NonNull Application application, @NonNull EntityCreateData data,
            @NonNull ThunkExpression<Boolean> permitCreatePredicate) throws QueryEngineException {
        return runInWriteTransaction(() ->
                delegate.create(application, data, permitCreatePredicate)
        );
    }

    @Override
    public UpdateResult update(@NonNull Application application, @NonNull EntityData data,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException {
        return runInWriteTransaction(() ->
                delegate.update(application, data, permitUpdatePredicate)
        );
    }

    @Override
    public Optional<EntityData> delete(@NonNull Application application, @NonNull EntityRequest entityRequest,
            @NonNull ThunkExpression<Boolean> permitDeletePredicate) throws QueryEngineException {
        return runInWriteTransaction(() ->
                delegate.delete(application, entityRequest, permitDeletePredicate)
        );
    }

    @Override
    public void deleteAll(@NonNull Application application, @NonNull Entity entity) throws QueryEngineException {
        runInWriteTransaction(() -> {
            delegate.deleteAll(application, entity);
            return null;
        });
    }

    @Override
    public boolean isLinked(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull EntityId targetId, @NonNull ThunkExpression<Boolean> permitReadPredicate)
            throws QueryEngineException {
        return runInReadOnlyTransaction(() ->
                delegate.isLinked(application, relationRequest, targetId, permitReadPredicate)
        );
    }

    @Override
    public Optional<EntityIdAndVersion> findTarget(@NonNull Application application,
            @NonNull RelationRequest relationRequest, @NonNull ThunkExpression<Boolean> permitReadPredicate)
            throws QueryEngineException {
        return runInReadOnlyTransaction(() ->
                delegate.findTarget(application, relationRequest, permitReadPredicate)
        );
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull EntityId targetId, @NonNull ThunkExpression<Boolean> permitUpdatePredicate)
            throws QueryEngineException {
        runInWriteTransaction(() -> {
            delegate.setLink(application, relationRequest, targetId, permitUpdatePredicate);
            return null;
        });
    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull ThunkExpression<Boolean> permitUpdatePredicate) throws QueryEngineException {
        runInWriteTransaction(() -> {
            delegate.unsetLink(application, relationRequest, permitUpdatePredicate);
            return null;
        });
    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull Set<EntityId> targetIds, @NonNull ThunkExpression<Boolean> permitUpdatePredicate)
            throws QueryEngineException {
        runInWriteTransaction(() -> {
            delegate.addLinks(application, relationRequest, targetIds, permitUpdatePredicate);
            return null;
        });
    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull RelationRequest relationRequest,
            @NonNull Set<EntityId> targetIds, @NonNull ThunkExpression<Boolean> permitUpdatePredicate)
            throws QueryEngineException {
        runInWriteTransaction(() -> {
            delegate.removeLinks(application, relationRequest, targetIds, permitUpdatePredicate);
            return null;
        });
    }

    @Override
    public ItemCount count(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression) throws QueryEngineException {
        return runInReadOnlyTransaction(() ->
                delegate.count(application, entity, expression)
        );
    }
}
