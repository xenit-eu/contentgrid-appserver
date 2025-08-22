package com.contentgrid.appserver.rest.assembler.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Value;
import org.springframework.hateoas.server.core.Relation;

public sealed interface ProfileAttributeConstraintRepresentationModel {

    @JsonProperty
    String getType();

    static AllowedValuesConstraintRepresentationModel allowedValues(List<String> values) {
        return new AllowedValuesConstraintRepresentationModel(values);
    }

    static RequiredConstraintRepresentationModel required() {
        return new RequiredConstraintRepresentationModel();
    }

    static UniqueConstraintRepresentationModel unique() {
        return new UniqueConstraintRepresentationModel();
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

    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    final class RequiredConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {

        @Override
        public String getType() {
            return "required";
        }
    }

    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    final class UniqueConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {

        @Override
        public String getType() {
            return "unique";
        }
    }
}
