package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.i18n.ResourceBundleTranslatable;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.flags.HiddenSearchFilterFlag;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.rest.EncodedCursorPaginationHandlerMethodArgumentResolver;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider.CollectionParameters;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions;
import org.springframework.hateoas.mediatype.html.HtmlInputType;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

@RequiredArgsConstructor
public class HalFormsTemplateGenerator {
    private final Application application;
    private final UserLocales userLocales;
    private final LinkFactoryProvider linkFactoryProvider;
    private static final ResourceBundleTranslatable<FieldTranslations, FieldTranslations> fieldTranslations = ResourceBundleTranslatable.builder(() -> new FieldTranslations())
            .bundleName(HalFormsTemplateGenerator.class.getName())
            .mapping("sort", FieldTranslations::withSortField)
            .build()
            .withPrefix("field.");
    private static final ResourceBundleTranslatable<SortDirectionTranslations, SortDirectionTranslations> sortDirectionTranslations = ResourceBundleTranslatable.builder(
                    SortDirectionTranslations::new)
            .bundleName(HalFormsTemplateGenerator.class.getName())
            .mapping("asc", SortDirectionTranslations::withSortAsc)
            .mapping("desc", SortDirectionTranslations::withSortDesc)
            .build()
            .withPrefix("sort.");

    private record PrefixSettings(
            String name,
            String prompt
    ) {
        PrefixSettings append(PrefixSettings settings) {
            return new PrefixSettings(
                    doAppend(name, ".", settings.name()),
                    doAppend(prompt, ": ", settings.prompt())
            );
        }

        private static String doAppend(String stringA, String separator, String stringB) {
            if(!stringA.isEmpty() && !stringB.isEmpty()) {
                return stringA + separator + stringB;
            }
            return stringA + stringB;
        }

        static PrefixSettings empty() {
            return new PrefixSettings("", "");
        }
    }


    public HalFormsTemplate generateCreateTemplate(EntityName entityName) {
        var entity = application.getRequiredEntityByName(entityName);
        List<HalFormsProperty> properties = new ArrayList<>();
        for (var attribute : entity.getAttributes()) {
            properties.addAll(attributeToCreateProperties(PrefixSettings.empty(), attribute));
        }
        for (var relation : application.getRelationsForSourceEntity(entity)) {
            // TODO: enable *-to-many relations in create form with ACC-2311
            if (relation instanceof OneToOneRelation || relation instanceof ManyToOneRelation) {
                relationToProperty(relation).ifPresent(properties::add);
            }
        }

        var hasFiles = properties.stream().anyMatch(prop -> Objects.equals(HtmlInputType.FILE_VALUE, prop.getType()));

        return HalFormsTemplate.builder()
                .key(IanaLinkRelations.CREATE_FORM_VALUE)
                .httpMethod(HttpMethod.POST)
                .contentType(hasFiles? MediaType.MULTIPART_FORM_DATA_VALUE:MediaType.APPLICATION_JSON_VALUE)
                .properties(properties)
                .target(linkFactoryProvider.toCollection(entityName, CollectionParameters.defaults()).toUri().toString())
                .build();
    }

    public HalFormsTemplate generateUpdateTemplate(EntityName entityName) {
        var entity = application.getRequiredEntityByName(entityName);
        List<HalFormsProperty> properties = new ArrayList<>();
        for (var attribute : entity.getAttributes()) {
            properties.addAll(attributeToUpdateProperty(PrefixSettings.empty(), attribute));
        }

        return HalFormsTemplate.builder()
                .key(HalFormsTemplate.DEFAULT_KEY)
                .httpMethod(HttpMethod.PUT)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .properties(properties)
                .build();
    }

    public HalFormsTemplate generateSearchTemplate(EntityName entityName) {
        var entity = application.getRequiredEntityByName(entityName);
        List<HalFormsProperty> properties = new ArrayList<>();
        for (var searchFilter : entity.getSearchFilters()) {
            if(searchFilter.hasFlag(HiddenSearchFilterFlag.class)) {
                continue;
            }
            if (searchFilter instanceof AttributeSearchFilter attributeSearchFilter) {
                var attribute = application.resolvePropertyPath(entity, attributeSearchFilter.getAttributePath());
                var property = HalFormsProperty.named(attributeSearchFilter.getName().getValue())
                        .withPrompt(attributeSearchFilter.getTranslations(userLocales).getName())
                        .withAttributeType(attribute.getType());
                properties.add(addAllowedValues(property, attribute, true));
            } else {
                throw new IllegalStateException("Unexpected value: " + searchFilter);
            }
        }
        entityToSortProperty(entity).ifPresent(properties::add);

        return HalFormsTemplate.builder()
                .key(IanaLinkRelations.SEARCH_VALUE)
                .httpMethod(HttpMethod.GET)
                .properties(properties)
                .target(linkFactoryProvider.toCollection(entityName, CollectionParameters.defaults()).toUri().toString())
                .build();
    }

    public List<HalFormsTemplate> generateRelationTemplates(RelationIdentity relationIdentity) {

        var relation = application.getRequiredRelationForEntity(relationIdentity.getEntityName(), relationIdentity.getRelationName());

        var maybeProperty = relationToProperty(relation);
        if (maybeProperty.isEmpty()) {
            return List.of();
        }

        var relationLink = linkFactoryProvider.toRelation(relationIdentity).toUri().toString();

        var result = new ArrayList<HalFormsTemplate>();
        if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
            result.add(HalFormsTemplate.builder()
                    .key("add-" + relation.getSourceEndPoint().getLinkName())
                    .httpMethod(HttpMethod.POST)
                    .target(relationLink)
                    .contentType("text/uri-list")
                    .property(maybeProperty.get())
                    .build());
        } else {
            result.add(HalFormsTemplate.builder()
                    .key("set-" + relation.getSourceEndPoint().getLinkName())
                    .httpMethod(HttpMethod.PUT)
                    .target(relationLink)
                    .contentType("text/uri-list")
                    .property(maybeProperty.get())
                    .build());
        }
        if (!relation.getSourceEndPoint().isRequired() && !relation.getTargetEndPoint().isRequired()) {
            // A relation that is required on any side can't be cleared, because it would give a constraint violation error
            result.add(HalFormsTemplate.builder()
                    .key("clear-" + relation.getSourceEndPoint().getLinkName())
                    .httpMethod(HttpMethod.DELETE)
                    .target(relationLink)
                    .build());
        }
        return result;
    }

    public List<HalFormsTemplate> generateContentTemplates(Entity entity, ContentAttribute content) {
        return List.of(); // no templates yet
    }


    private List<HalFormsProperty> attributeToCreateProperties(PrefixSettings prefix, Attribute attribute) {
        if (attribute.isIgnored() || attribute.isReadOnly()) {
            return List.of();
        }
        var result = new ArrayList<HalFormsProperty>();
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                result.add(simpleAttributeToProperty(prefix, simpleAttribute));
            }
            case UserAttribute userAttribute -> {
                // how to provide user attributes at creation?
            }
            case ContentAttribute contentAttribute -> {
                result.add(contentToCreateProperty(prefix, contentAttribute));
            }
            case CompositeAttribute compositeAttribute -> {
                var newPrefix = prefix.append(new PrefixSettings(compositeAttribute.getName().getValue(), compositeAttribute.getTranslations(userLocales).getName()));
                for (var attr : compositeAttribute.getAttributes()) {
                    result.addAll(attributeToCreateProperties(newPrefix, attr));
                }
            }
        }
        return result;
    }

    private List<HalFormsProperty> attributeToUpdateProperty(PrefixSettings prefix, Attribute attribute) {
        if (attribute.isIgnored() || attribute.isReadOnly()) {
            return List.of();
        }
        var result = new ArrayList<HalFormsProperty>();
        switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                result.add(simpleAttributeToProperty(prefix, simpleAttribute));
            }
            case UserAttribute userAttribute -> {
                // how to update user attributes?
            }
            // handle ContentAttributes like CompositeAttributes
            case CompositeAttribute compositeAttribute -> {
                var newPrefix = prefix.append(new PrefixSettings(compositeAttribute.getName().getValue(), compositeAttribute.getTranslations(userLocales).getName()));
                for (var attr : compositeAttribute.getAttributes()) {
                    result.addAll(attributeToUpdateProperty(newPrefix, attr));
                }
            }
        }
        return result;
    }

    private HalFormsProperty simpleAttributeToProperty(PrefixSettings prefix, SimpleAttribute attribute) {
        var prefixed = prefix.append(new PrefixSettings(attribute.getName().getValue(), attribute.getTranslations(userLocales).getName()));

        var property = HalFormsProperty.named(prefixed.name())
                .withPrompt(prefixed.prompt())
                .withAttributeType(attribute.getType())
                .withRequired(attribute.hasConstraint(RequiredConstraint.class));
        return addAllowedValues(property, attribute, false);
    }

    private HalFormsProperty addAllowedValues(HalFormsProperty property, SimpleAttribute attribute, boolean unbounded) {
        return attribute.getConstraint(AllowedValuesConstraint.class)
                .map(AllowedValuesConstraint::getValues)
                .map(HalFormsOptions::inline)
                .map(options -> options.withMinItems(property.isRequired() ? 1L : 0L))
                .map(options -> options.withMaxItems(unbounded ? null : 1L))
                .map(property::withOptions)
                .orElse(property);
    }

    private Optional<HalFormsProperty> relationToProperty(Relation relation) {
        if (relation.getSourceEndPoint().hasFlag(HiddenEndpointFlag.class)) {
            return Optional.empty();
        }
        var required = relation.getSourceEndPoint().isRequired();
        var url = linkFactoryProvider.toCollection(relation.getTargetEndPoint().getEntity(), CollectionParameters.defaults())
                .withSelfRel();
        var options = HalFormsOptions.remote(url)
                .withMinItems(required ? 1L : 0L)
                .withMaxItems(relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation ? null : 1L)
                .withValueField("/_links/self/href");
        return Optional.of(HalFormsProperty.named(relation.getSourceEndPoint().getName().getValue())
                .withType(HtmlInputType.URL_VALUE)
                .withPrompt(relation.getSourceEndPoint().getTranslations(userLocales).getName())
                .withRequired(required)
                .withOptions(options));
    }

    private HalFormsProperty contentToCreateProperty(PrefixSettings prefix, ContentAttribute attribute) {
        var prefixed = prefix.append(new PrefixSettings(attribute.getName().getValue(), attribute.getTranslations(userLocales).getName()));
        return HalFormsProperty.named(prefixed.name())
                .withPrompt(prefixed.prompt())
                .withType(HtmlInputType.FILE_VALUE);
    }

    private Optional<HalFormsProperty> entityToSortProperty(Entity entity) {
        var sortOptions = new ArrayList<SortOption>();
        for (var sortableField : entity.getSortableFields()) {
            var attribute = application.resolvePropertyPath(entity, sortableField.getPropertyPath());
            sortableFieldToSortOptions(attribute, sortableField)
                    .forEachOrdered(sortOptions::add);
        }
        if (sortOptions.isEmpty()) {
            return Optional.empty();
        }
        var options = HalFormsOptions.inline(sortOptions)
                .withMinItems(0L)
                .withPromptField("prompt")
                .withValueField("value");
        return Optional.of(HalFormsProperty.named(EncodedCursorPaginationHandlerMethodArgumentResolver.SORT_NAME)
                .withPrompt(fieldTranslations.getTranslations(userLocales).getSortField())
                .withType(HtmlInputType.TEXT_VALUE)
                .withOptions(options));
    }

    private Stream<SortOption> sortableFieldToSortOptions(SimpleAttribute attribute, SortableField sortableField) {
        var translations = sortDirectionTranslations
                .withSuffixes(List.of("."+attribute.getType().name().toLowerCase(Locale.ROOT), ""))
                .getTranslations(userLocales);
        return Stream.of(Direction.ASC, Direction.DESC)
                .map(direction -> {
                    var directionName = direction.name().toLowerCase(Locale.ROOT);
                    return new SortOption(
                            sortableField.getPropertyPath().toString(),
                            directionName,
                            translations.getPrompt(direction, attribute.getTranslations(userLocales).getName()),
                            sortableField.getName().getValue() + "," + directionName
                    );
                });
    }

    @With
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static class SortDirectionTranslations {
        public SortDirectionTranslations(@NonNull Locale locale) {
            this(locale, null, null);
        }

        @With(value = AccessLevel.NONE)
        @NonNull
        private final Locale locale;

        private final String sortAsc;
        private final String sortDesc;


        public String getPrompt(@NonNull Direction direction, @NonNull String attributeName) {
            var translatedSortDirection = switch (direction) {
                case ASC -> sortAsc;
                case DESC -> sortDesc;
            };

            var fmt = new MessageFormat(translatedSortDirection, locale);

            return fmt.format(new Object[] {attributeName});
        }

    }

    @Value
    private static class SortOption {
        String property;
        String direction;
        String prompt;
        String value;
    }

    @With
    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @NoArgsConstructor(force = true)
    private static class FieldTranslations {
        String sortField;
    }
}
