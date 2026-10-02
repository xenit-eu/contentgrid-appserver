package com.contentgrid.appserver.rest.mapping.specialized;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.mapping.SpecializedOnEntity;
import com.contentgrid.appserver.rest.mapping.specialized.PathParameterSpecializer.PathParameterPatternsSupplier;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

class SpecializedOnEntityAnnotationSpecialisationHandler implements SpecializationHandler<SpecializedOnEntity> {

    @Override
    public Optional<Specializer> getSpecializerFor(SpecializedOnEntity annotation) {
        return Optional.of(new PathParameterSpecializer(new PathParameterPatternsSupplier() {
            @Override
            public Set<String> getPathParameters() {
                return Set.of(annotation.entityPathVariable());
            }

            @Override
            public Stream<Map<String, String>> getPathParameterValues(Application application) {
                return application.getEntities().stream()
                        .map(e -> Map.of(annotation.entityPathVariable(), e.getPathSegment().getValue()));
            }
        }));
    }
}
