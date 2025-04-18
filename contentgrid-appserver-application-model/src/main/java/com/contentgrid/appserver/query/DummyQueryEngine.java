package com.contentgrid.appserver.query;

import com.contentgrid.appserver.application.model.Entity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class DummyQueryEngine implements QueryEngine {

    private final List<EntityInstance> persons = Stream.of(
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
    ).map(DummyEntityInstance::fromMap).toList();

    @Override
    public List<EntityInstance> query(Entity entity, Map<String, String> filters) {
        if (entity.getName().getValue().equals("person")) {
            return persons;
        } else{
            return List.of();
        }
    }

    @Override
    public Optional<EntityInstance> findById(Entity entity, String id) {
        if (entity.getName().getValue().equals("person")) {
            return persons.stream().filter(p -> p.getId().equals(id)).findAny();
        }
        return Optional.empty();
    }
}
