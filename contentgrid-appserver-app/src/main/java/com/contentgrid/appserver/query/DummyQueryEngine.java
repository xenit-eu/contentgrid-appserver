package com.contentgrid.appserver.query;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.EntityDataValidator;
import com.contentgrid.appserver.query.ItemCountPage.ItemCount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class DummyQueryEngine implements QueryEngine {

    protected final Map<String, List<EntityInstance>> entityInstances = new ConcurrentHashMap<>();

    public DummyQueryEngine() {
        entityInstances.put("person", new ArrayList<>(Stream.of(
                Map.of(
                        "first_name", "John",
                        "last_name", "Doe",
                        "birth_date", Instant.ofEpochSecond(876543210)
                ),
                Map.of(
                        "first_name", "Alice",
                        "last_name", "Aaronson",
                        "birth_date", Instant.ofEpochSecond(765432100)
                )
        ).map(DummyEntityInstance::fromMap).toList()));
    }

    @Override
    public ItemCountPage<EntityInstance> query(Entity entity, Map<String, String> filters, PageRequest pageRequest) {
        String entityName = entity.getName().getValue();
        var res = entityInstances.getOrDefault(entityName, List.of());
        return new ItemCountPageImpl<>(res, new ItemCount(res.size(), true));
    }

    @Override
    public Optional<EntityInstance> findById(Entity entity, String id) {
        String entityName = entity.getName().getValue();
        return entityInstances.getOrDefault(entityName, List.of()).stream()
                .filter(p -> p.getId().equals(id))
                .findAny();
    }

    @Override
    public EntityInstance createInstance(Entity entity, Map<String, Object> data) {
        String entityName = entity.getName().getValue();

        // Validate and convert data according to entity attribute definitions
        Map<String, Object> validatedData = EntityDataValidator.validate(entity, data);

        EntityInstance instance = DummyEntityInstance.fromMap(validatedData);
        entityInstances.computeIfAbsent(entityName, k -> new ArrayList<>()).add(instance);
        return instance;
    }
}
