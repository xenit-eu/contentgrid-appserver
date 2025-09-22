package com.contentgrid.appserver.rest.assembler.profile.hal;

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
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.rest.assembler.profile.hal.ProfileEntityRepresentationModelAssembler.Context;
import com.contentgrid.appserver.rest.assembler.profile.hal.ProfileSearchParamRepresentationModel.ProfileSearchParamType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProfileAttributeRepresentationModelAssembler {

    public Optional<ProfileAttributeRepresentationModel> toModel(Context context, Entity entity, Attribute attribute) {
        return toModel(context, entity, new SimpleAttributePath(attribute.getName()), attribute);
    }

    private Optional<ProfileAttributeRepresentationModel> toModel(Context context, Entity entity, AttributePath path, Attribute attribute) {
        if (attribute.isIgnored()) {
            return Optional.empty();
        }

        var model = switch (attribute) {
            case SimpleAttribute simpleAttribute -> simpleAttributeToModel(context, entity, path, simpleAttribute);
            case UserAttribute userAttribute -> userAttributeToModel(context, userAttribute);
            case ContentAttribute contentAttribute -> compositeAttributeToModel(context, entity, path, contentAttribute);
            case CompositeAttribute compositeAttribute -> compositeAttributeToModel(context, entity, path, compositeAttribute);
        };
        return Optional.of(model);
    }

    private ProfileAttributeRepresentationModel compositeAttributeToModel(Context context, Entity entity, AttributePath path, CompositeAttribute compositeAttribute) {
        var attributes = compositeAttribute.getAttributes().stream()
                .map(attribute -> toModel(context, entity, path.withSuffix(attribute.getName()), attribute))
                .flatMap(Optional::stream)
                .toList();

        var translations = compositeAttribute.getTranslations(context.userLocales());
        return ProfileAttributeRepresentationModel.builder()
                .name(compositeAttribute.getName().getValue())
                .title(translations.getName())
                .type(ProfileAttributeType.OBJECT)
                .description(translations.getDescription())
                .readOnly(compositeAttribute.isReadOnly())
                .attributes(attributes)
                .build();
    }

    private ProfileAttributeRepresentationModel userAttributeToModel(Context context, UserAttribute userAttribute) {
        var translations = userAttribute.getTranslations(context.userLocales());
        return ProfileAttributeRepresentationModel.builder()
                .name(userAttribute.getName().getValue())
                .title(translations.getName())
                .type(ProfileAttributeType.STRING)
                .description(translations.getDescription())
                .readOnly(true)
                .build();
    }

    private ProfileAttributeRepresentationModel simpleAttributeToModel(Context context, Entity entity, AttributePath path, SimpleAttribute attribute) {
        var constraints = attribute.getConstraints().stream()
                .map(this::attributeConstraintToModel)
                .toList();

        var searchParams = entity.getSearchFilters().stream()
                .filter(AttributeSearchFilter.class::isInstance)
                .map(AttributeSearchFilter.class::cast)
                .filter(filter -> filter.getAttributePath().equals(path))
                .map(filter -> attributeSearchFilterToModel(context, filter))
                .flatMap(Optional::stream)
                .toList();

        var translations = attribute.getTranslations(context.userLocales());

        return ProfileAttributeRepresentationModel.builder()
                .name(attribute.getName().getValue())
                .title(translations.getName())
                .description(translations.getDescription())
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

    private Optional<ProfileSearchParamRepresentationModel> attributeSearchFilterToModel(Context context, AttributeSearchFilter filter) {
        if (filter.hasFlag(HiddenSearchFilterFlag.class)) {
            return Optional.empty();
        }

        var translations = filter.getTranslations(context.userLocales());
        return Optional.of(ProfileSearchParamRepresentationModel.builder()
                .name(filter.getName().getValue())
                .title(translations.getName())
                .type(ProfileSearchParamType.from(filter))
                .build());
    }

}
