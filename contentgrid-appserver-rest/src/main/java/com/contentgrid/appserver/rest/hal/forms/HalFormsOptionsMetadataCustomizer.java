package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.HasAttributes;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.CompositeAttributePath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.hal.forms.property.PropertyMetadataWithOptions;
import com.contentgrid.hateoas.spring.affordances.property.CustomPropertyMetadata;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationCustomizer;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsConfiguration;

@RequiredArgsConstructor
public class HalFormsOptionsMetadataCustomizer implements MediaTypeConfigurationCustomizer<HalFormsConfiguration> {

    @NonNull
    private final Supplier<List<Application>> applications;

    @Override
    public HalFormsConfiguration customize(HalFormsConfiguration configuration) {
        var configAtomic = new AtomicReference<>(configuration);
        findAllPropertiesWithOptions()
                .forEach(propertyName -> {
                    configAtomic.updateAndGet(config -> config.withOptions(Object.class, propertyName, propertyMetadata -> {
                        return CustomPropertyMetadata.custom(propertyMetadata)
                                .findDelegate(PropertyMetadataWithOptions.class)
                                .map(PropertyMetadataWithOptions::getOptions)
                                .orElse(null);
                    }));
                });
        return configAtomic.get();
    }

    private Set<String> findAllPropertiesWithOptions() {
        var result = new HashSet<String>();
        for (var application : applications.get()) {
            for (var entity : application.getEntities()) {
                // find attributes with allowed values constraint
                var attrWithPaths = getAttributeWithPaths(entity);
                for (var attrWithPath : attrWithPaths.toList()) {
                    if (attrWithPath.attribute().hasConstraint(AllowedValuesConstraint.class)) {
                        result.add(attrWithPath.path().toString());
                    }
                }
                // find searchFilters with allowed values constraint
                for (var searchFilter : entity.getSearchFilters()) {
                    if (searchFilter instanceof AttributeSearchFilter attributeSearchFilter) {
                        var attribute = application.resolvePropertyPath(entity,
                                attributeSearchFilter.getAttributePath());
                        if (attribute.hasConstraint(AllowedValuesConstraint.class)) {
                            result.add(attributeSearchFilter.getName().getValue());
                        }
                    }
                }
                // all named relations
                for (var relation : application.getRelationsForSourceEntity(entity)) {
                    if (relation.getSourceEndPoint().getName() != null) {
                        result.add(relation.getSourceEndPoint().getName().getValue());
                    }
                }
            }
        }
        // sort property
        result.add(EntityRestController.SORT_NAME);
        return result;
    }

    private Stream<AttributeWithPath> getAttributeWithPaths(HasAttributes container) {
        var builder = Stream.<AttributeWithPath>builder();
        for (var attribute : container.getAttributes()) {
            switch (attribute) {
                case SimpleAttribute simpleAttribute -> {
                    builder.add(new AttributeWithPath(simpleAttribute, new SimpleAttributePath(simpleAttribute.getName())));
                } case CompositeAttribute compositeAttribute -> {
                    getAttributeWithPaths(compositeAttribute)
                            .map(attrWithPath -> new AttributeWithPath(attrWithPath.attribute(), new CompositeAttributePath(compositeAttribute.getName(), attrWithPath.path())))
                            .forEachOrdered(builder::add);
                }
            }
        }
        return builder.build();
    }

    private record AttributeWithPath(SimpleAttribute attribute, AttributePath path) {}
}
