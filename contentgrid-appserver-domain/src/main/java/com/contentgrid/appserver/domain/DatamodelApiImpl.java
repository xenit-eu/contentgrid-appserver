package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DatamodelApiImpl implements DatamodelApi {
    private final QueryEngine queryEngine;

    @Override
    public SliceData findAll(@NonNull Application application, @NonNull Entity entity,
            @NonNull Map<String, String> params, PageData pageData)
            throws EntityNotFoundException, InvalidThunkExpressionException {
        ThunkExpression<Boolean> filter = ThunkExpressionGenerator.from(application, entity, params);
        return queryEngine.findAll(application, entity, filter, pageData);
    }

    @Override
    public Optional<EntityData> findById(@NonNull Application application, @NonNull Entity entity, @NonNull EntityId id)
            throws EntityNotFoundException {
        return queryEngine.findById(application, entity, id);
    }

    @Override
    public EntityId create(@NonNull Application application, @NonNull EntityData data,
            @NonNull List<RelationData> relations) throws QueryEngineException {
        return queryEngine.create(application, data, relations);
    }

    @Override
    public void update(@NonNull Application application, @NonNull EntityId id, @NonNull EntityData data) throws QueryEngineException {
        var dataWithId = EntityData.builder()
                .id(id)
                .name(data.getName())
                .attributes(data.getAttributes())
                .build();
        queryEngine.update(application, dataWithId);
    }
}
