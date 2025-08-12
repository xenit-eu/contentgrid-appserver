package com.contentgrid.appserver.rest.data.conversion;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.hateoas.spring.links.UriTemplateMatcher;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;

@RequiredArgsConstructor
public class StringDataEntryToRelationDataEntryConverter implements Converter<StringDataEntry, RelationDataEntry> {
    @NonNull
    private final Application application;

    @Override
    public RelationDataEntry convert(StringDataEntry source) {
        var value = source.getValue();
        var matcher = UriTemplateMatcher.<RelationDataEntry>builder()
                .matcherFor(methodOn(EntityRestController.class)
                                .getEntity(null, null, null),
                        params -> {
                            var entityPathSegment = params.get("entityName");
                            var entityId = params.get("instanceId");
                            var entity = application.getEntityByPathSegment(PathSegmentName.of(entityPathSegment))
                                    .orElseThrow(() -> new IllegalArgumentException("Invalid entity URL '%s': no entity mapped to path '%s'".formatted(value, entityPathSegment)));

                            return new RelationDataEntry(
                                    entity.getName(),
                                    EntityId.of(UUID.fromString(entityId))
                            );
                        })
                .build();

        return matcher.tryMatch(value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid entity URL '%s'".formatted(value)));
    }
}
