package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SliceData;
import com.contentgrid.appserver.query.engine.api.data.SliceData.PageInfo;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.NonNull;
import org.springframework.core.convert.ConversionService;


public class DummyQueryEngine implements QueryEngine {
    private final ConversionService conversionService;

    protected final Map<String, List<EntityData>> entityInstances = new ConcurrentHashMap<>();

    public DummyQueryEngine(Application application, ConversionService conversionService) {
        this.conversionService = conversionService;

        var personEntity = application.getEntityByName(EntityName.of("person"));
        if (personEntity.isPresent()) {
            entityInstances.put("person", new ArrayList<>(Stream.of(
                            Map.of(
                                    "id", UUID.fromString("12181b10-8af6-42f9-bde0-46b4c28cd0d9"),
                                    "first_name", "John",
                                    "last_name", "Doe",
                                    "birth_date", Instant.ofEpochSecond(876543210)
                            ),
                            Map.of(
                                    "id", UUID.fromString("34307d24-3109-42f4-abca-7007b92694a8"),
                                    "first_name", "Alice",
                                    "last_name", "Aaronson",
                                    "birth_date", Instant.ofEpochSecond(765432100)
                            )
                    ).map(m -> DummyEntityInstance.fromMap(m, personEntity.get()))
                    .toList()));
        }
    }


    @Override
    public SliceData findAll(@NonNull Application application, @NonNull Entity entity,
            @NonNull ThunkExpression<Boolean> expression, PageData pageData)
            throws QueryEngineException {
        var entities = entityInstances.get(entity.getName().getValue());
        return SliceData.builder()
                .entities(entities)
                .pageInfo(PageInfo.builder()
                        .start(0L)
                        .size((long) entities.size())
                        .exactCount((long) entities.size())
                        .estimatedCount((long) entities.size())
                        .build())
                .build();
    }

    @Override
    public Optional<EntityData> findById(@NonNull Application application, @NonNull Entity entity, @NonNull Object id) {
        var idName = entity.getPrimaryKey().getName();
        return entityInstances.getOrDefault(entity.getName().getValue(), List.of()).stream()
                .filter(e -> e.getAttributeByName(idName)
                        .map(a -> ((SimpleAttributeData) a).getValue())
                        .map(a -> conversionService.convert(a, id.getClass()))
                        .map(a -> a.equals(id))
                        .orElse(false))
                .findAny();
    }

    @Override
    public Object create(@NonNull Application application, @NonNull EntityData data,
            @NonNull List<RelationData> relations) throws QueryEngineException {
        var instances = entityInstances.getOrDefault(data.getName().getValue(), new ArrayList<>());
        var entity = application.getEntityByName(data.getName());
        if (entity.isEmpty()) {
            return null;
        }
        instances.add(data);
        entityInstances.put(data.getName().getValue(), instances);
        return ((SimpleAttributeData) data.getAttributeByName(entity.get().getPrimaryKey().getName()).orElseThrow()).getValue();
    }

    @Override
    public void update(@NonNull Application application, @NonNull EntityData data) throws QueryEngineException {

    }

    @Override
    public void delete(@NonNull Application application, @NonNull Entity entity, @NonNull Object id)
            throws QueryEngineException {

    }

    @Override
    public void deleteAll(@NonNull Application application, @NonNull Entity entity) throws QueryEngineException {

    }

    @Override
    public boolean isLinked(@NonNull Application application, @NonNull Relation relation, @NonNull Object sourceId,
            @NonNull Object targetId) throws QueryEngineException {
        return false;
    }

    @Override
    public RelationData findLink(@NonNull Application application, @NonNull Relation relation, @NonNull Object id)
            throws QueryEngineException {
        return null;
    }

    @Override
    public void setLink(@NonNull Application application, @NonNull RelationData data, @NonNull Object id)
            throws QueryEngineException {

    }

    @Override
    public void unsetLink(@NonNull Application application, @NonNull Relation relation, @NonNull Object id)
            throws QueryEngineException {

    }

    @Override
    public void addLinks(@NonNull Application application, @NonNull XToManyRelationData<?> data, @NonNull Object id)
            throws QueryEngineException {

    }

    @Override
    public void removeLinks(@NonNull Application application, @NonNull XToManyRelationData<?> data, @NonNull Object id)
            throws QueryEngineException {

    }
}
