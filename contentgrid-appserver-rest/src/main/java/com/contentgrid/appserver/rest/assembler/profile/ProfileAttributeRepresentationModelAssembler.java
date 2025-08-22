package com.contentgrid.appserver.rest.assembler.profile;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.flags.HiddenSearchFilterFlag;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.rest.assembler.profile.ProfileSearchParamRepresentationModel.ProfileSearchParamType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProfileAttributeRepresentationModelAssembler {

    public Optional<ProfileAttributeRepresentationModel> toModel(Application application, Entity entity, Attribute attribute) {
        return toModel(application, entity, new SimpleAttributePath(attribute.getName()), attribute);
    }

    private Optional<ProfileAttributeRepresentationModel> toModel(Application application, Entity entity, AttributePath path, Attribute attribute) {
        if (attribute.isIgnored()) {
            return Optional.empty();
        }

        var model = switch (attribute) {
            case SimpleAttribute simpleAttribute -> simpleAttributeToModel(application, entity, path, simpleAttribute);
            case UserAttribute userAttribute -> userAttributeToModel(application, entity, path, userAttribute);
            case ContentAttribute contentAttribute -> compositeAttributeToModel(application, entity, path, contentAttribute);
            case CompositeAttribute compositeAttribute -> compositeAttributeToModel(application, entity, path, compositeAttribute);
        };
        return Optional.of(model);
    }

    private ProfileAttributeRepresentationModel compositeAttributeToModel(Application application, Entity entity, AttributePath path, CompositeAttribute compositeAttribute) {
        var attributes = compositeAttribute.getAttributes().stream()
                .map(attribute -> toModel(application, entity, path.withSuffix(attribute.getName()), attribute))
                .flatMap(Optional::stream)
                .toList();

        return ProfileAttributeRepresentationModel.builder()
                .name(compositeAttribute.getName().getValue())
                .title(readTitle(application, entity, path))
                .type(ProfileAttributeType.OBJECT)
                .description(compositeAttribute.getDescription())
                .readOnly(compositeAttribute.isReadOnly())
                .attributes(attributes)
                .build();
    }

    private ProfileAttributeRepresentationModel userAttributeToModel(Application application, Entity entity, AttributePath path, UserAttribute userAttribute) {
        return ProfileAttributeRepresentationModel.builder()
                .name(userAttribute.getName().getValue())
                .title(readTitle(application, entity, path))
                .type(ProfileAttributeType.STRING)
                .description(userAttribute.getDescription())
                .readOnly(true)
                .build();
    }

    private ProfileAttributeRepresentationModel simpleAttributeToModel(Application application, Entity entity, AttributePath path, SimpleAttribute attribute) {
        var constraints = attribute.getConstraints().stream()
                .map(this::attributeConstraintToModel)
                .toList();

        var searchParams = entity.getSearchFilters().stream()
                .filter(AttributeSearchFilter.class::isInstance)
                .map(AttributeSearchFilter.class::cast)
                .filter(filter -> filter.getAttributePath().equals(path))
                .map(filter -> attributeSearchFilterToModel(application, entity, filter))
                .flatMap(Optional::stream)
                .toList();

        return ProfileAttributeRepresentationModel.builder()
                .name(attribute.getName().getValue())
                .title(readTitle(application, entity, path))
                .description(attribute.getDescription())
                .type(ProfileAttributeType.from(attribute.getType()))
                .required(attribute.hasConstraint(RequiredConstraint.class))
                .readOnly(attribute.isReadOnly())
                .constraints(constraints)
                .searchParams(searchParams)
                .build();
    }

    private ProfileAttributeConstraintRepresentationModel attributeConstraintToModel(Constraint constraint) {
        return switch (constraint) {
            case RequiredConstraint ignored -> ProfileAttributeConstraintRepresentationModel.required();
            case UniqueConstraint ignored -> ProfileAttributeConstraintRepresentationModel.unique();
            case AllowedValuesConstraint allowedValuesConstraint ->
                ProfileAttributeConstraintRepresentationModel.allowedValues(allowedValuesConstraint.getValues());
        };
    }

    private Optional<ProfileSearchParamRepresentationModel> attributeSearchFilterToModel(Application application,
            Entity entity, AttributeSearchFilter filter) {
        if (filter.hasFlag(HiddenSearchFilterFlag.class)) {
            return Optional.empty();
        }
        return Optional.of(ProfileSearchParamRepresentationModel.builder()
                .name(filter.getName().getValue())
                .title(readPrompt(application, entity, filter))
                .type(ProfileSearchParamType.from(filter))
                .build());
    }

    private String readTitle(Application application, Entity entity, PropertyPath path) {
        return null; // TODO: resolve title messages (ACC-2230)
    }

    private String readPrompt(Application application, Entity entity, AttributeSearchFilter filter) {
        return null; // TODO: resolve prompt messages (ACC-2230)
    }

}
