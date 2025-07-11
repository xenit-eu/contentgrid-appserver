package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.ArrayList;
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
            @NonNull Map<String, String> params, SortData sort, PageData pageData)
            throws EntityNotFoundException, InvalidThunkExpressionException {
        ThunkExpression<Boolean> filter = ThunkExpressionGenerator.from(application, entity, params);
        validateSortData(entity, sort);
        return queryEngine.findAll(application, entity, filter, sort, pageData);
    }

    private void validateSortData(Entity entity, SortData sortData) {
        for (FieldSort field : sortData.getSortedFields()) {
            var name = field.getName();
            entity.getSortableFieldByName(name).orElseThrow(() ->
                    InvalidSortParameterException.invalidField(name.getValue(), entity.getName().getValue()));
        }
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
        var entity = application.getEntityByName(data.getName()).orElseThrow();
        ArrayList<AttributeData> attributeData = new ArrayList<>();
        for (Attribute attr : entity.getAttributes()) {
            var given = data.getAttributeByName(attr.getName());
            if (given.isPresent()) {
                attributeData.add(given.get());
            } else if (attr instanceof SimpleAttribute){
                attributeData.add(SimpleAttributeData.builder().name(attr.getName()).build());
            } else {
                // Creating CompositeAttributes is for when we support content TODO ACC-2097
            }
        }

        var dataWithId = EntityData.builder()
                .id(id)
                .name(data.getName())
                .attributes(attributeData)
                .build();
        queryEngine.update(application, dataWithId);
    }

    @Override
    public void updatePartial(@NonNull Application application, @NonNull EntityId id, @NonNull EntityData data) throws QueryEngineException {
        var dataWithId = EntityData.builder()
                .id(id)
                .name(data.getName())
                .attributes(data.getAttributes())
                .build();
        queryEngine.update(application, dataWithId);
    }

    @Override
    public boolean hasRelationTarget(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId sourceId,
            @NonNull EntityId targetId) throws QueryEngineException {
        return queryEngine.isLinked(application, relation, sourceId, targetId);
    }

    @Override
    public Optional<EntityId> findRelationTarget(@NonNull Application application, @NonNull Relation relation,
            @NonNull EntityId id) throws QueryEngineException {
        return queryEngine.findTarget(application, relation, id);
    }

    @Override
    public void setRelation(@NonNull Application application, @NonNull XToOneRelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        queryEngine.setLink(application, data, id);
    }

    @Override
    public void deleteRelation(@NonNull Application application, @NonNull Relation relation, @NonNull EntityId id)
            throws QueryEngineException {
        queryEngine.unsetLink(application, relation, id);
    }

    @Override
    public void addRelationItems(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        queryEngine.addLinks(application, data, id);
    }

    @Override
    public void removeRelationItems(@NonNull Application application, @NonNull XToManyRelationData data, @NonNull EntityId id)
            throws QueryEngineException {
        queryEngine.removeLinks(application, data, id);
    }
}
