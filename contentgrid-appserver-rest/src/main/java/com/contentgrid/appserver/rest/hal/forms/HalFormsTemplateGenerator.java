package com.contentgrid.appserver.rest.hal.forms;

import static com.contentgrid.appserver.application.model.openapi.model.rest.body.MediaType.FORM;
import static com.contentgrid.appserver.application.model.openapi.model.rest.body.MediaType.MULTIPART_FORM;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RegexPatternConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.i18n.ResourceBundleTranslatable;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ArrayBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyObjectMapper;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyObjectMapper.Context;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyType;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ContentBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ObjectBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.RelationBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.SimpleBodyValue;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
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
import java.util.stream.Collectors;
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
import org.springframework.hateoas.mediatype.hal.forms.HalFormsOptions.AbstractHalFormsOptions;
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

    public HalFormsTemplate generateCreateTemplate(EntityName entityName) {
        var body = BodyObjectMapper.forBody(new Context(application, BodyType.POST, MULTIPART_FORM, userLocales), entityName);
        var properties = toHalFormsProperties(body);
        var hasFiles = properties.stream().anyMatch(prop -> Objects.equals(HtmlInputType.FILE_VALUE, prop.getType()));
        return HalFormsTemplate.builder()
                .key(IanaLinkRelations.CREATE_FORM_VALUE)
                .httpMethod(HttpMethod.POST)
                .contentType(hasFiles ? MediaType.MULTIPART_FORM_DATA_VALUE : MediaType.APPLICATION_JSON_VALUE)
                .properties(properties)
                .target(linkFactoryProvider.toCollection(entityName, CollectionParameters.defaults()).toUri().toString())
                .build();
    }

    public HalFormsTemplate generateUpdateTemplate(EntityName entityName) {
        var body = BodyObjectMapper.forBody(new Context(application, BodyType.PUT, FORM, userLocales), entityName);
        var properties = toHalFormsProperties(body);
        return HalFormsTemplate.builder()
                .key(HalFormsTemplate.DEFAULT_KEY)
                .httpMethod(HttpMethod.PUT)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .properties(properties)
                .build();
    }

    public HalFormsTemplate generateSearchTemplate(EntityName entityName) {
        var entity = application.getRequiredEntityByName(entityName);
        var body = BodyObjectMapper.forSearch(application, userLocales, entityName);
        var properties = toHalFormsProperties(body)
                .stream()
                // Search forms don't have regex constraints, because searches are looser (e.g. search for prefix or full-text search)
                .map(p -> p.withRegex(null))
                .collect(Collectors.toList());
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

        var relationLink = linkFactoryProvider.toRelation(relationIdentity).orElseThrow().toUri().toString();

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

    private List<HalFormsProperty> toHalFormsProperties(ObjectBodyValue body) {
        var result = new ArrayList<HalFormsProperty>();
        for (var entry : body.getFields().entrySet()) {
            result.add(toHalFormsProperty(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private HalFormsProperty toHalFormsProperty(String name, BodyValue bodyValue) {
        var prop =  switch (bodyValue) {
            case SimpleBodyValue sv -> {
                var property = HalFormsProperty.named(name)
                        .withAttributeType(sv.getType());
                for (var constraint : sv.getConstraints()) {
                    if (constraint instanceof RegexPatternConstraint rc) {
                        property = property.withRegex(rc.getHtmlPattern());
                    } else if (constraint instanceof AllowedValuesConstraint avc) {
                        var options = HalFormsOptions.inline(avc.getValues())
                                .withMinItems(sv.isMandatory() ? 1L : 0L)
                                .withMaxItems(1L);
                        property = property.withOptions(options);
                    }
                }
                yield property;
            }
            case ObjectBodyValue ov -> throw new IllegalArgumentException("Cannot use ObjectBodyValue");
            case RelationBodyValue rv -> {
                var url = linkFactoryProvider.toCollection(rv.getTargetEntity(), CollectionParameters.defaults())
                        .withSelfRel();
                var options = HalFormsOptions.remote(url)
                        .withMinItems(rv.isMandatory() ? 1L : 0L)
                        .withMaxItems(1L)
                        .withValueField("/_links/self/href");
                yield HalFormsProperty.named(name)
                        .withType(HtmlInputType.URL_VALUE)
                        .withOptions(options);
            }
            case ContentBodyValue cv -> HalFormsProperty.named(name)
                    .withType(HtmlInputType.FILE_VALUE);
            case ArrayBodyValue av -> {
                var item = toHalFormsProperty(name, av.getItems());
                var options = item.getOptions();
                if (options == null) {
                    options = HalFormsOptions.inline();
                }
                if (options instanceof AbstractHalFormsOptions<?> halFormsOptions) {
                    // Set max items to unlimited when we have an array
                    options = halFormsOptions.withMaxItems(null);
                }
                yield item.withOptions(options);
            }
        };
        if(bodyValue.getTitle() != null) {
            prop = prop.withPrompt(bodyValue.getTitle());
        }

        return prop
                .withRequired(bodyValue.isMandatory() && !bodyValue.isNullable());
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
