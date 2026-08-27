package com.contentgrid.appserver.rest.profile.assembler.hal;

import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RegexPatternConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.searchfilters.BaseAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.flags.HiddenSearchFilterFlag;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import com.contentgrid.appserver.rest.profile.assembler.hal.ProfileEntityRepresentationModelAssembler.Context;
import com.contentgrid.appserver.rest.profile.assembler.hal.ProfileSearchParamRepresentationModel.ProfileSearchParamType;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
            case SimpleAttribute simpleAttribute -> attributeToModel(context, entity, path, simpleAttribute,
                    simpleAttribute.getConstraints(), simpleAttribute.hasConstraint(RequiredConstraint.class),
                    ProfileAttributeType.from(simpleAttribute.getType()));
            case MultivalueAttribute multivalueAttribute -> attributeToModel(context, entity, path,
                    multivalueAttribute, multivalueAttribute.getConstraints(),
                    multivalueAttribute.hasConstraint(RequiredConstraint.class), ProfileAttributeType.STRING_SET);
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
        var constraints = userAttribute.getFlags().stream()
                .map(this::attributeFlagToModel)
                .flatMap(Optional::stream)
                .toList();

        return ProfileAttributeRepresentationModel.builder()
                .name(userAttribute.getName().getValue())
                .title(translations.getName())
                .type(ProfileAttributeType.STRING)
                .description(translations.getDescription())
                .readOnly(true)
                .constraints(constraints)
                .build();
    }

    private ProfileAttributeRepresentationModel attributeToModel(Context context, Entity entity, AttributePath path,
            Attribute attribute, List<Constraint> attributeConstraints, boolean required, ProfileAttributeType type) {
        var constraints = Stream.concat(
                attributeConstraints.stream()
                        .map(this::attributeConstraintToModel),
                attribute.getFlags().stream()
                        .map(this::attributeFlagToModel)
                        .flatMap(Optional::stream)
                ).toList();

        var searchParams = entity.getSearchFilters().stream()
                .filter(BaseAttributeSearchFilter.class::isInstance)
                .map(BaseAttributeSearchFilter.class::cast)
                .filter(filter -> filter.getAttributePath().equals(path))
                .map(filter -> attributeSearchFilterToModel(context, filter))
                .flatMap(Optional::stream)
                .toList();

        var translations = attribute.getTranslations(context.userLocales());

        return ProfileAttributeRepresentationModel.builder()
                .name(attribute.getName().getValue())
                .title(translations.getName())
                .description(translations.getDescription())
                .type(type)
                .required(required)
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
            case RegexPatternConstraint regexPatternConstraint ->
                ProfileAttributeConstraintRepresentationModel.pattern(regexPatternConstraint.getHtmlPattern());
        };
    }

    private Optional<ProfileAttributeConstraintRepresentationModel> attributeFlagToModel(AttributeFlag flag) {
        return switch (flag) {
            case CreatedDateFlag createdDateFlag -> Optional.of(ProfileAttributeConstraintRepresentationModel.createdDate());
            case CreatorFlag creatorFlag -> Optional.of(ProfileAttributeConstraintRepresentationModel.createdBy());
            case ModifiedDateFlag modifiedDateFlag -> Optional.of(ProfileAttributeConstraintRepresentationModel.modifiedDate());
            case ModifierFlag modifierFlag -> Optional.of(ProfileAttributeConstraintRepresentationModel.modifiedBy());
            default -> Optional.empty();
        };
    }

    private Optional<ProfileSearchParamRepresentationModel> attributeSearchFilterToModel(Context context, BaseAttributeSearchFilter filter) {
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
