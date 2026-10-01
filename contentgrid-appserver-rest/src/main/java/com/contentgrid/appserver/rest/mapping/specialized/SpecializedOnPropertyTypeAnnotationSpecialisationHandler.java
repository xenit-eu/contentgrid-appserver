package com.contentgrid.appserver.rest.mapping.specialized;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.specialized.PathParameterSpecializer.PathParameterPatternsSupplier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

class SpecializedOnPropertyTypeAnnotationSpecialisationHandler implements
        SpecializationHandler<SpecializedOnPropertyType> {

    @Override
    public Optional<Specializer> getSpecializerFor(SpecializedOnPropertyType annotation) {
        return Optional.of(new PathParameterSpecializer(new PathParameterPatternsSupplier() {
            @Override
            public Set<String> getPathParameters() {
                return Set.of(annotation.entityPathVariable(), annotation.propertyPathVariable());
            }

            @Override
            public Stream<Map<String, String>> getPathParameterValues(Application application) {
                return Arrays.stream(annotation.type())
                        .flatMap(type -> getReplacementVariablesGenerator(type).generateForApplication(application))
                        .map(vars -> Map.of(
                                annotation.entityPathVariable(), vars.entityPathSegment().getValue(),
                                annotation.propertyPathVariable(), vars.propertyPathSegment().getValue()
                        ));
            }
        }));
    }

    private static ReplacementPathVariablesGenerator getReplacementVariablesGenerator(
            SpecializedOnPropertyType.PropertyType propertyType) {
        return switch (propertyType) {
            case CONTENT_ATTRIBUTE -> new ContentAttributeReplacementPathVariablesGenerator();
            case TO_ONE_RELATION ->
                    new RelationReplacementPathVariablesGenerator(OneToOneRelation.class, ManyToOneRelation.class);
            case TO_MANY_RELATION ->
                    new RelationReplacementPathVariablesGenerator(OneToManyRelation.class, ManyToManyRelation.class);
        };
    }

    /**
     * Generates {@link ReplacementPathVariableValues} for a specific application
     * <p>
     * Specific implementations are used in the {@link SpecializedOnPropertyType.PropertyType} to generate replacement values for that specific type
     */
    private interface ReplacementPathVariablesGenerator {

        /**
         * @param application The application to generate replacement path variable values for
         * @return Replacement values for path variables
         */
        Stream<ReplacementPathVariableValues> generateForApplication(Application application);

        /**
         * Replacement values to use for path variables to specialize a path pattern
         *
         * @param entityPathSegment The replacement value for the {@link SpecializedOnPropertyType#entityPathVariable()}
         * @param propertyPathSegment The replacement value for the {@link SpecializedOnPropertyType#propertyPathVariable()}
         */
        record ReplacementPathVariableValues(
                @NonNull PathSegmentName entityPathSegment,
                @NonNull PathSegmentName propertyPathSegment
        ) {

        }
    }

    private static class ContentAttributeReplacementPathVariablesGenerator implements
            ReplacementPathVariablesGenerator {

        @Override
        public Stream<ReplacementPathVariableValues> generateForApplication(@NonNull Application application) {
            return application.getEntities().stream()
                    .flatMap(entity -> entity.getContentAttributes().stream()
                            .map(contentAttribute -> new ReplacementPathVariableValues(entity.getPathSegment(),
                                    contentAttribute.getPathSegment()))
                    );
        }
    }

    @RequiredArgsConstructor
    private static class RelationReplacementPathVariablesGenerator implements
            ReplacementPathVariablesGenerator {

        private final Set<Class<? extends Relation>> supportedRelationClasses;

        @SafeVarargs
        public RelationReplacementPathVariablesGenerator(
                @NonNull Class<? extends Relation>... supportedRelationClasses) {
            this(Set.of(supportedRelationClasses));
        }

        @Override
        public Stream<ReplacementPathVariableValues> generateForApplication(@NonNull Application application) {
            var result = application.getRelations()
                    .stream()
                    .flatMap(relation -> Stream.of(relation, relation.inverse()))
                    .filter(this::isSupported)
                    .map(Relation::getSourceEndPoint)
                    .filter(endpoint -> endpoint.getPathSegment() != null)
                    .map(endpoint -> new ReplacementPathVariableValues(application.getRequiredEntityByName(
                            endpoint.getEntity()).getPathSegment(), endpoint.getPathSegment()))
                    .toList();
            return result.stream();
        }

        private boolean isSupported(Relation relation) {
            return supportedRelationClasses.stream()
                    .anyMatch(relationClass -> relationClass.isInstance(relation));
        }
    }
}
