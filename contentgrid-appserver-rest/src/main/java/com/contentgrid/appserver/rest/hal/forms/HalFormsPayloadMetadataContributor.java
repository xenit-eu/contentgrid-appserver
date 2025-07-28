package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.rest.hal.forms.property.PropertyMetadataWithInlineValues;
import com.contentgrid.appserver.rest.hal.forms.property.RelationPropertyMetadata;
import com.contentgrid.appserver.rest.hal.forms.property.SortPropertyMetadata;
import com.contentgrid.hateoas.spring.affordances.property.BasicPropertyMetadata;
import java.io.File;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.core.ResolvableType;
import org.springframework.hateoas.AffordanceModel.PropertyMetadata;
import org.springframework.hateoas.mediatype.html.HtmlInputType;

public class HalFormsPayloadMetadataContributor {

    public Stream<PropertyMetadata> contributeToCreateForm(Application application, Entity entity) {
        var builder = Stream.<PropertyMetadata>builder();
        for (var attribute : entity.getAttributes()) {
            var metadata = attributeToCreatePropertyMetadata("", attribute);
            metadata.forEachOrdered(builder::add);
        }
        for (var relation : application.getRelationsForSourceEntity(entity)) {
            relationToCreatePropertyMetadata(application, relation).ifPresent(builder::add);
        }
        return builder.build();
    }

    public Stream<PropertyMetadata> contributeToUpdateForm(Application application, Entity entity) {
        var builder = Stream.<PropertyMetadata>builder();
        for (var attribute : entity.getAttributes()) {
            var metadata = attributeToUpdatePropertyMetadata("", attribute);
            metadata.forEachOrdered(builder::add);
        }
        return builder.build();
    }

    public Stream<PropertyMetadata> contributeToSearchForm(Application application, Entity entity) {
        var builder = Stream.<PropertyMetadata>builder();
        for (var searchFilter : entity.getSearchFilters()) {
            if (Objects.requireNonNull(searchFilter) instanceof AttributeSearchFilter attributeSearchFilter) {
                var propertyMetadata = searchFilterToSearchPropertyMetadata(attributeSearchFilter);
                var attribute = application.resolvePropertyPath(entity, attributeSearchFilter.getAttributePath());
                builder.add(addAllowedValues(propertyMetadata, attribute, true));
            } else {
                throw new IllegalStateException("Unexpected value: " + searchFilter);
            }
        }
        if (!entity.getSortableFields().isEmpty()) {
            builder.add(new SortPropertyMetadata(entity.getSortableFields()));
        }
        return builder.build();
    }

    private Stream<PropertyMetadata> attributeToCreatePropertyMetadata(String prefix, Attribute attribute) {
        if (attribute.isIgnored() || attribute.isReadOnly()) {
            return Stream.of();
        }
        var result = Stream.<PropertyMetadata>builder();
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                result.add(simpleAttributeToPropertyMetadata(prefix, simpleAttribute));
            }
            case UserAttribute userAttribute -> {
                // how to provide user attributes at creation?
            }
            case ContentAttribute contentAttribute -> {
                result.add(contentToCreatePropertyMetadata(prefix, contentAttribute));
            }
            case CompositeAttribute compositeAttribute -> {
                var newPrefix = prefix + compositeAttribute.getName() + ".";
                for (var attr : compositeAttribute.getAttributes()) {
                    var metadata = attributeToCreatePropertyMetadata(newPrefix, attr);
                    metadata.forEachOrdered(result::add);
                }
            }
        }
        return result.build();
    }

    private Stream<PropertyMetadata> attributeToUpdatePropertyMetadata(String prefix, Attribute attribute) {
        if (attribute.isIgnored() || attribute.isReadOnly()) {
            return Stream.empty();
        }
        var result = Stream.<PropertyMetadata>builder();
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                result.add(simpleAttributeToPropertyMetadata(prefix, simpleAttribute));
            }
            case UserAttribute userAttribute -> {
                // how to update user attributes?
            }
            // handle ContentAttributes like CompositeAttributes
            case CompositeAttribute compositeAttribute -> {
                var newPrefix = prefix + compositeAttribute.getName() + ".";
                for (var attr : compositeAttribute.getAttributes()) {
                    var metadata = attributeToUpdatePropertyMetadata(newPrefix, attr);
                    metadata.forEachOrdered(result::add);
                }
            }
        }
        return result.build();
    }

    private PropertyMetadata simpleAttributeToPropertyMetadata(String prefix, SimpleAttribute attribute) {
        var propertyMetadata = new BasicPropertyMetadata(prefix + attribute.getName().getValue(), attributeTypeToResolvableType(attribute.getType()))
                .withRequired(attribute.hasConstraint(RequiredConstraint.class))
                .withReadOnly(false);
        return addAllowedValues(propertyMetadata, attribute, false);
    }

    private PropertyMetadata addAllowedValues(PropertyMetadata propertyMetadata, SimpleAttribute attribute, boolean unbounded) {
        return attribute.getConstraint(AllowedValuesConstraint.class)
                .map(AllowedValuesConstraint::getValues)
                .map(values -> new PropertyMetadataWithInlineValues(propertyMetadata, values, unbounded))
                .map(PropertyMetadata.class::cast)
                .orElse(propertyMetadata);
    }

    private Optional<PropertyMetadata> relationToCreatePropertyMetadata(Application application, Relation relation) {
        if (relation.getSourceEndPoint().getName() == null) {
            return Optional.empty();
        } else {
            return Optional.of(new RelationPropertyMetadata(application, relation));
        }
    }

    private PropertyMetadata contentToCreatePropertyMetadata(String prefix, ContentAttribute attribute) {
        return new BasicPropertyMetadata(prefix + attribute.getName().getValue(), ResolvableType.forClass(File.class))
                .withInputType(HtmlInputType.FILE_VALUE)
                .withRequired(false)
                .withReadOnly(false);
    }

    private PropertyMetadata searchFilterToSearchPropertyMetadata(AttributeSearchFilter attributeSearchFilter) {
        return new BasicPropertyMetadata(attributeSearchFilter.getName().getValue(), attributeTypeToResolvableType(attributeSearchFilter.getAttributeType()))
                .withReadOnly(false)
                .withRequired(false);
    }

    private static ResolvableType attributeTypeToResolvableType(Type type) {
        return ResolvableType.forClass(attributeTypeToClass(type));
    }

    private static Class<?> attributeTypeToClass(Type type) {
        return switch (type) {
            case TEXT -> String.class;
            case LONG -> Long.class;
            case DOUBLE -> Double.class;
            case BOOLEAN -> Boolean.class;
            case DATETIME -> Instant.class;
            case UUID -> UUID.class;
        };
    }

}
