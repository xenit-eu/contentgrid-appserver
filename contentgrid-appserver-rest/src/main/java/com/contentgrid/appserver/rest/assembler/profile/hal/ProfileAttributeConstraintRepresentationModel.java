package com.contentgrid.appserver.rest.assembler.profile.hal;

import com.contentgrid.appserver.rest.assembler.profile.BlueprintLinkRelations;
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

    static CreatedDateConstraintRepresentationModel createdDate() {
        return new CreatedDateConstraintRepresentationModel();
    }

    static CreatedByConstraintRepresentationModel createdBy() {
        return new CreatedByConstraintRepresentationModel();
    }

    static ModifiedDateConstraintRepresentationModel modifiedDate() {
        return new ModifiedDateConstraintRepresentationModel();
    }

    static ModifiedByConstraintRepresentationModel modifiedBy() {
        return new ModifiedByConstraintRepresentationModel();
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

    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    final class CreatedDateConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {

        @Override
        public String getType() {
            return "created-date";
        }
    }

    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    final class CreatedByConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {

        @Override
        public String getType() {
            return "created-by";
        }
    }

    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    final class ModifiedDateConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {

        @Override
        public String getType() {
            return "modified-date";
        }
    }

    @Relation(BlueprintLinkRelations.CONSTRAINT_STRING)
    final class ModifiedByConstraintRepresentationModel implements ProfileAttributeConstraintRepresentationModel {

        @Override
        public String getType() {
            return "modified-by";
        }
    }

}
