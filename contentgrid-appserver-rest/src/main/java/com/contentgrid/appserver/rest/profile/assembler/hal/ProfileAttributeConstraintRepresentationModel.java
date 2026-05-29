package com.contentgrid.appserver.rest.profile.assembler.hal;

import com.contentgrid.appserver.rest.profile.assembler.BlueprintLinkRelations;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.NonNull;
import lombok.Value;
import org.springframework.hateoas.server.core.Relation;

public sealed interface ProfileAttributeConstraintRepresentationModel {

    @JsonProperty
    String getType();

    static AllowedValuesConstraintRepresentationModel allowedValues(List<String> values) {
        return new AllowedValuesConstraintRepresentationModel(values);
    }

    static PatternAttributeConstraintRepresentationModel pattern(@NonNull String pattern) {
        return new PatternAttributeConstraintRepresentationModel(pattern);
    }

    static ProfileAttributeConstraintRepresentationModel required() {
        return new FlagConstraintRepresentationModel("required");
    }

    static ProfileAttributeConstraintRepresentationModel unique() {
        return new FlagConstraintRepresentationModel("unique");
    }

    static ProfileAttributeConstraintRepresentationModel createdDate() {
        return new FlagConstraintRepresentationModel("created-date");
    }

    static ProfileAttributeConstraintRepresentationModel createdBy() {
        return new FlagConstraintRepresentationModel("created-by");
    }

    static ProfileAttributeConstraintRepresentationModel modifiedDate() {
        return new FlagConstraintRepresentationModel("modified-date");
    }

    static ProfileAttributeConstraintRepresentationModel modifiedBy() {
        return new FlagConstraintRepresentationModel("modified-by");
    }

    @Value
    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    class AllowedValuesConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {

        List<String> values;

        @Override
        public String getType() {
            return "allowed-values";
        }
    }

    @Value
    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    class PatternAttributeConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {
        String pattern;

        @Override
        public String getType() {
            return "pattern";
        }
    }

    @Value
    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    class FlagConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {
        String type;
    }
}
