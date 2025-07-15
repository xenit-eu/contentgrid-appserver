package com.contentgrid.appserver.rest.data.conversion;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.hateoas.spring.links.UriTemplateMatcher;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringToRelationDataEntryConverter implements Converter<String, RelationDataEntry> {
    @NonNull
    private final Application application;

    @Override
    public RelationDataEntry convert(String source) {
        var matcher = UriTemplateMatcher.<RelationDataEntry>builder()
                .matcherFor(methodOn(EntityRestController.class)
                                .getEntity(null, null, null),
                        params -> {
                            var entityPathSegment = params.get("entityName");
                            var entityId = params.get("instanceId");
                            var entity = application.getEntityByPathSegment(PathSegmentName.of(entityPathSegment))
                                    .orElseThrow(() -> new IllegalArgumentException("Invalid entity URL '%s': no entity mapped to path '%s'".formatted(source, entityPathSegment)));

                            return new RelationDataEntry(
                                    entity.getName(),
                                    EntityId.of(UUID.fromString(entityId))
                            );
                        })
                .build();

        return matcher.tryMatch(source)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid entity URL '%s'".formatted(source)));
    }
}
