package com.contentgrid.appserver.application.model.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity.ConfigurableEntityTranslations;
import com.contentgrid.appserver.application.model.Entity.EntityTranslations;
import com.contentgrid.appserver.application.model.attributes.Attribute.AttributeTranslations;
import com.contentgrid.appserver.application.model.attributes.Attribute.ConfigurableAttributeTranslations;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.i18n.Translatable;
import com.contentgrid.appserver.application.model.json.exceptions.InvalidEntityLinkTemplateException;
import com.contentgrid.appserver.application.model.json.exceptions.InvalidPropertyPathException;
import com.contentgrid.appserver.application.model.links.EntityLink;
import com.contentgrid.appserver.application.model.links.LinkIdentity;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.AutomationUriTemplateDefinition;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.EntityLinkSubstitutionVariables;
import com.contentgrid.appserver.application.model.links.UriTemplateDefinition.SimpleUriTemplateDefinition;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath;
import com.contentgrid.appserver.application.model.propertypath.PropertyPath.ResolvesToAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint.ConfigurableRelationEndPointTranslations;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint.RelationEndPointTranslations;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.relations.flags.RelationEndpointFlag;
import com.contentgrid.appserver.application.model.relations.flags.RequiredEndpointFlag;
import com.contentgrid.appserver.application.model.relations.flags.VisibleEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.searchfilters.BaseAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter.ConfigurableSearchFilterTranslations;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter.SearchFilterTranslations;
import com.contentgrid.appserver.application.model.searchfilters.flags.HiddenSearchFilterFlag;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.searchfilters.flags.SyntheticSearchFilterFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SchemaName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.application.model.json.exceptions.InvalidJsonException;
import com.contentgrid.appserver.application.model.json.exceptions.UnknownFilterTypeException;
import com.contentgrid.appserver.application.model.json.exceptions.UnknownFlagException;
import com.contentgrid.appserver.application.model.json.model.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.json.model.ContentEncryptionSettings;
import com.contentgrid.appserver.application.model.json.model.ApplicationSchema;
import com.contentgrid.appserver.application.model.json.model.ApplicationSettings;
import com.contentgrid.appserver.application.model.json.model.Attribute;
import com.contentgrid.appserver.application.model.json.model.AttributeConstraint;
import com.contentgrid.appserver.application.model.json.model.CompositeAttribute;
import com.contentgrid.appserver.application.model.json.model.ContentAttribute;
import com.contentgrid.appserver.application.model.json.model.DatabaseSettings;
import com.contentgrid.appserver.application.model.json.model.Entity;
import com.contentgrid.appserver.application.model.json.model.ManyToManyRelation;
import com.contentgrid.appserver.application.model.json.model.OneToManyRelation;
import com.contentgrid.appserver.application.model.json.model.OneToOneRelation;
import com.contentgrid.appserver.application.model.json.model.PatternConstaint;
import com.contentgrid.appserver.application.model.json.model.PropertyPathElement;
import com.contentgrid.appserver.application.model.json.model.PropertyPathElement.PropertyPathElementType;
import com.contentgrid.appserver.application.model.json.model.Relation;
import com.contentgrid.appserver.application.model.json.model.RelationEndPoint;
import com.contentgrid.appserver.application.model.json.model.RequiredConstraint;
import com.contentgrid.appserver.application.model.json.model.SearchFilter;
import com.contentgrid.appserver.application.model.json.model.SimpleAttribute;
import com.contentgrid.appserver.application.model.json.model.SortableField;
import com.contentgrid.appserver.application.model.json.model.Translations;
import com.contentgrid.appserver.application.model.json.model.Translations.EmptyTranslation;
import com.contentgrid.appserver.application.model.json.model.Translations.MultipleTranslations;
import com.contentgrid.appserver.application.model.json.model.Translations.SingleTranslation;
import com.contentgrid.appserver.application.model.json.model.UniqueConstraint;
import com.contentgrid.appserver.application.model.json.model.UserAttribute;
import com.contentgrid.appserver.application.model.json.validation.ApplicationSchemaValidator;
import com.contentgrid.hateoas.uritemplate.InvalidUriTemplateException;
import com.contentgrid.hateoas.uritemplate.ParameterizedUriTemplate;
import com.contentgrid.hateoas.uritemplate.ParameterizedUriTemplateParser;
import java.util.EnumSet;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultApplicationSchemaConverter implements ApplicationSchemaConverter {

    private static final String FTS_TYPE = "full-text";

    private final JsonMapper mapper = ApplicationSchemaJsonMapperFactory.createJsonMapper();
    private final ApplicationSchemaValidator validator = new ApplicationSchemaValidator();

    private static final TranslationConverter<ConfigurableEntityTranslations, Entity> ENTITY_TRANSLATIONS = TranslationConverter.<ConfigurableEntityTranslations, Entity>builder()
            .mapper(Entity::getTitle, ConfigurableEntityTranslations::withSingularName)
            .mapper(Entity::getCollectionTitle, ConfigurableEntityTranslations::withPluralName)
            .mapper(Entity::getDescription, ConfigurableEntityTranslations::withDescription)
            .build();

    private static final TranslationConverter<ConfigurableAttributeTranslations, Attribute> ATTRIBUTE_TRANSLATIONS = TranslationConverter.<ConfigurableAttributeTranslations, Attribute>builder()
            .mapper(Attribute::getTitle, ConfigurableAttributeTranslations::withName)
            .mapper(Attribute::getDescription, ConfigurableAttributeTranslations::withDescription)
            .build();

    private static final TranslationConverter<ConfigurableSearchFilterTranslations, SearchFilter> SEARCH_FILTER_TRANSLATIONS = TranslationConverter.<ConfigurableSearchFilterTranslations, SearchFilter>builder()
            .mapper(SearchFilter::getTitle, ConfigurableSearchFilterTranslations::withName)
            .mapper(SearchFilter::getDescription, ConfigurableSearchFilterTranslations::withDescription)
            .build();

    private static final TranslationConverter<ConfigurableRelationEndPointTranslations, RelationEndPoint> RELATION_ENDPOINT_TRANSLATIONS = TranslationConverter.<ConfigurableRelationEndPointTranslations, RelationEndPoint>builder()
            .mapper(RelationEndPoint::getTitle, ConfigurableRelationEndPointTranslations::withName)
            .mapper(RelationEndPoint::getDescription, ConfigurableRelationEndPointTranslations::withDescription)
            .build();


    @Override
    public Application convert(InputStream json) throws InvalidJsonException {
        var schema = getApplicationSchema(json);
        List<com.contentgrid.appserver.application.model.Entity> entities = new ArrayList<>();
        for (Entity entity : schema.getEntities()) {
            com.contentgrid.appserver.application.model.Entity convertEntity = fromJsonEntity(entity);
            entities.add(convertEntity);
        }
        List<com.contentgrid.appserver.application.model.relations.Relation> relations = new ArrayList<>();
        if (schema.getRelations() != null) {
            for (Relation rel : schema.getRelations()) {
                com.contentgrid.appserver.application.model.relations.Relation relation = fromJsonRelation(rel);
                relations.add(relation);
            }
        }
        com.contentgrid.appserver.application.model.settings.ApplicationSettings applicationSettings = null;
        if (schema.getSettings() != null) {
            applicationSettings = fromJsonApplicationSettings(schema.getSettings());
        }
        return Application.builder()
                .name(ApplicationName.of(
                        schema.getApplicationName()))
                .entities(entities)
                .relations(relations)
                .settings(applicationSettings)
                .build();
    }

    private ApplicationSchema getApplicationSchema(InputStream json) throws InvalidJsonException {
        try {
            var jsonString = new String(json.readAllBytes(), StandardCharsets.UTF_8);
            validator.validate(jsonString);
            return mapper.readValue(jsonString, ApplicationSchema.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private com.contentgrid.appserver.application.model.settings.ApplicationSettings fromJsonApplicationSettings(ApplicationSettings json) {
        var builder = com.contentgrid.appserver.application.model.settings.ApplicationSettings.builder();
        if (json.getContentEncryption() != null) {
            builder.contentEncryption(fromJsonContentEncryption(json.getContentEncryption()));
        }
        if (json.getDatabase() != null) {
            builder.database(fromJsonDatabaseSettings(json.getDatabase()));
        }
        return builder.build();
    }

    private com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings fromJsonContentEncryption(
            ContentEncryptionSettings json) {
        return com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings.builder()
                .enabled(json.isEnabled())
                .build();
    }

    private com.contentgrid.appserver.application.model.settings.database.DatabaseSettings fromJsonDatabaseSettings(
            DatabaseSettings json) {
        var builder = com.contentgrid.appserver.application.model.settings.database.DatabaseSettings.builder();
        if (json.getSchema() != null) {
            builder.schema(SchemaName.of(json.getSchema()));
        }
        return builder.build();
    }

    private com.contentgrid.appserver.application.model.Entity fromJsonEntity(
            Entity jsonEntity) throws InvalidJsonException {
        com.contentgrid.appserver.application.model.attributes.SimpleAttribute primaryKey = fromJsonSimpleAttribute(
                jsonEntity.getPrimaryKey());
        List<com.contentgrid.appserver.application.model.attributes.Attribute> attributes;
        if (jsonEntity.getAttributes() == null) {
            attributes = List.of();
        } else {
            attributes = fromJsonAttributes(jsonEntity.getAttributes());
        }
        List<com.contentgrid.appserver.application.model.searchfilters.SearchFilter> searchFilters;
        if (jsonEntity.getSearchFilters() == null) {
            searchFilters = List.of();
        } else {
            List<com.contentgrid.appserver.application.model.searchfilters.SearchFilter> list = new ArrayList<>();
            for (SearchFilter sf : jsonEntity.getSearchFilters()) {
                com.contentgrid.appserver.application.model.searchfilters.SearchFilter searchFilter = fromJsonSearchFilter(sf);
                list.add(searchFilter);
            }
            searchFilters = list;
        }
        List<com.contentgrid.appserver.application.model.sortable.SortableField> sortableFields;
        if (jsonEntity.getSortableFields() == null) {
            sortableFields = List.of();
        } else {
            List<com.contentgrid.appserver.application.model.sortable.SortableField> list = new ArrayList<>();
            for (SortableField sf : jsonEntity.getSortableFields()) {
                com.contentgrid.appserver.application.model.sortable.SortableField sortableField = fromJsonSortableField(sf);
                list.add(sortableField);
            }
            sortableFields = list;
        }

        List<EntityLink> links = new ArrayList<>();
        if(jsonEntity.getLinks() != null) {
            for (var jsonEntityLink : jsonEntity.getLinks()) {
                links.add(fromJsonEntityLink(jsonEntityLink));
            }
        }
        return ENTITY_TRANSLATIONS.mapInto(jsonEntity, com.contentgrid.appserver.application.model.Entity.builder())
                .name(EntityName.of(jsonEntity.getName()))
                .pathSegment(PathSegmentName.of(jsonEntity.getPathSegment()))
                .linkName(LinkName.of(jsonEntity.getLinkName()))
                .table(TableName.of(jsonEntity.getTable()))
                .primaryKey(primaryKey)
                .attributes(attributes)
                .searchFilters(searchFilters)
                .sortableFields(sortableFields)
                .links(links)
                .build();
    }

    private com.contentgrid.appserver.application.model.attributes.Attribute fromJsonAttribute(Attribute jsonAttr)
            throws UnknownFlagException {
        return switch (jsonAttr) {
            case SimpleAttribute sa -> fromJsonSimpleAttribute(sa);
            case CompositeAttribute ca -> fromJsonCompositeAttribute(ca);
            case ContentAttribute ca -> fromJsonContentAttribute(ca);
            case UserAttribute ua -> fromJsonUserAttribute(ua);
        };
    }

    private com.contentgrid.appserver.application.model.attributes.SimpleAttribute fromJsonSimpleAttribute(
            SimpleAttribute jsonAttr) throws UnknownFlagException {
        List<Constraint> constraints =
                jsonAttr.getConstraints() == null ? List.of() : jsonAttr.getConstraints().stream()
                        .map(this::fromJsonAttributeConstraint)
                        .toList();
        return ATTRIBUTE_TRANSLATIONS.mapInto(jsonAttr, com.contentgrid.appserver.application.model.attributes.SimpleAttribute.builder())
                .name(AttributeName.of(jsonAttr.getName()))
                .column(ColumnName.of(jsonAttr.getColumnName()))
                .type(Type.valueOf(jsonAttr.getDataType().toUpperCase()))
                .flags(fromJsonAttributeFlags(jsonAttr.getFlags()))
                .constraints(constraints)
                .build();
    }

    private com.contentgrid.appserver.application.model.attributes.CompositeAttribute fromJsonCompositeAttribute(
            CompositeAttribute ca) throws UnknownFlagException {
        return ATTRIBUTE_TRANSLATIONS.mapInto(ca, CompositeAttributeImpl.builder())
                .name(AttributeName.of(ca.getName()))
                .flags(fromJsonAttributeFlags(ca.getFlags()))
                .attributes(fromJsonAttributes(ca.getAttributes()))
                .build();
    }

    private List<com.contentgrid.appserver.application.model.attributes.Attribute> fromJsonAttributes(
            List<Attribute> attributes) throws UnknownFlagException {
        List<com.contentgrid.appserver.application.model.attributes.Attribute> list = new ArrayList<>();
        for (Attribute attribute : attributes) {
            com.contentgrid.appserver.application.model.attributes.Attribute convertAttribute = fromJsonAttribute(
                    attribute);
            list.add(convertAttribute);
        }
        return list;
    }

    private com.contentgrid.appserver.application.model.attributes.ContentAttribute fromJsonContentAttribute(
            ContentAttribute ca) throws UnknownFlagException {
        return ATTRIBUTE_TRANSLATIONS.mapInto(ca, com.contentgrid.appserver.application.model.attributes.ContentAttribute.builder())
                .name(AttributeName.of(ca.getName()))
                .flags(fromJsonAttributeFlags(ca.getFlags()))
                .pathSegment(PathSegmentName.of(ca.getPathSegment()))
                .linkName(LinkName.of(ca.getLinkName()))
                .idColumn(ColumnName.of(ca.getIdColumn()))
                .filenameColumn(ColumnName.of(ca.getFileNameColumn()))
                .mimetypeColumn(ColumnName.of(ca.getMimeTypeColumn()))
                .lengthColumn(ColumnName.of(ca.getLengthColumn()))
                .build();
    }

    private com.contentgrid.appserver.application.model.attributes.UserAttribute fromJsonUserAttribute(
            UserAttribute ua) throws UnknownFlagException {
        return ATTRIBUTE_TRANSLATIONS.mapInto(ua, com.contentgrid.appserver.application.model.attributes.UserAttribute.builder())
                .name(AttributeName.of(ua.getName()))
                .flags(fromJsonAttributeFlags(ua.getFlags()))
                .idColumn(ColumnName.of(ua.getIdColumn()))
                .namespaceColumn(ColumnName.of(ua.getNamespaceColumn()))
                .usernameColumn(ColumnName.of(ua.getUserNameColumn()))
                .build();
    }

    private List<AttributeFlag> fromJsonAttributeFlags(List<String> flags) throws UnknownFlagException {
        if (flags == null) {
            return List.of();
        }
        List<AttributeFlag> result = new ArrayList<>();
        for (String flag : flags) {
            AttributeFlag convertFlag = fromJsonAttributeFlag(flag);
            result.add(convertFlag);
        }
        return result;
    }

    private AttributeFlag fromJsonAttributeFlag(String flag) throws UnknownFlagException {
        return switch (flag) {
            case "ignored" -> IgnoredFlag.INSTANCE;
            case "readOnly" -> ReadOnlyFlag.INSTANCE;
            case "createdDate" -> CreatedDateFlag.INSTANCE;
            case "creator" -> CreatorFlag.INSTANCE;
            case "eTag" -> ETagFlag.INSTANCE;
            case "modifiedDate" -> ModifiedDateFlag.INSTANCE;
            case "modifier" -> ModifierFlag.INSTANCE;
            default -> throw new UnknownFlagException("Unknown flag: " + flag);
        };
    }

    private Constraint fromJsonAttributeConstraint(AttributeConstraint constraint) {
        return switch (constraint) {
            case AllowedValuesConstraint avc -> Constraint.allowedValues(avc.getValues());
            case UniqueConstraint ignored -> Constraint.unique();
            case RequiredConstraint ignored -> Constraint.required();
            case PatternConstaint patternConstaint -> {
                var regexPatternConstraint = Constraint.pattern(patternConstaint.getRegex());
                if (patternConstaint.getHtmlPattern() != null) {
                    regexPatternConstraint = regexPatternConstraint.withHtmlPattern(patternConstaint.getHtmlPattern());
                }
                yield regexPatternConstraint;
            }
        };
    }

    private com.contentgrid.appserver.application.model.searchfilters.SearchFilter fromJsonSearchFilter(
            SearchFilter jsonFilter
    ) throws InvalidJsonException {
        var type = jsonFilter.getType();
        var propertyPath = fromJsonPropertyPath(jsonFilter.getAttributePath(), PropertyPath.ResolvesToAttribute.class);
        var filterName = FilterName.of(jsonFilter.getName());

        return type.equals(FTS_TYPE) ? fromJsonFullTextSearchFilter(jsonFilter, propertyPath, filterName)
                : fromJsonAttributeSearchFilter(jsonFilter, type, propertyPath, filterName);
    }

    private <T extends PropertyPath> T fromJsonPropertyPath(List<PropertyPathElement> propertyPath, Class<T> targetType) throws InvalidJsonException {
        var path = PropertyPath.of(propertyPath.stream().map(PropertyPathElement::toPropertyName).toList());
        try {
            return path.as(targetType);
        } catch (com.contentgrid.appserver.application.model.propertypath.InvalidPropertyPathException e) {
            throw new InvalidPropertyPathException(e.getMessage(), e);
        }
    }

    private FullTextSearchAttributeSearchFilter fromJsonFullTextSearchFilter(
            SearchFilter jsonFilter, ResolvesToAttribute propertyPath, FilterName filterName) throws InvalidJsonException {
        Locale locale = Objects.requireNonNull(jsonFilter.getLocale(), "Full-text search filters require a locale to be set.");

        return SEARCH_FILTER_TRANSLATIONS.mapInto(jsonFilter, FullTextSearchAttributeSearchFilter.builder())
                .name(filterName)
                .attributePath(propertyPath)
                .flags(fromJsonSearchFilterFlags(jsonFilter.getFlags()))
                .locale(locale)
                .build();
    }

    private com.contentgrid.appserver.application.model.searchfilters.SearchFilter fromJsonAttributeSearchFilter(
            SearchFilter jsonFilter, String type, PropertyPath.ResolvesToAttribute propertyPath, FilterName filterName) throws InvalidJsonException {
        var operation = switch (type) {
            case "prefix" -> Operation.PREFIX;
            case "exact" -> Operation.EXACT;
            case "greater" -> Operation.GREATER_THAN;
            case "greater-or-equal" -> Operation.GREATER_THAN_OR_EQUAL;
            case "less" -> Operation.LESS_THAN;
            case "less-or-equal" -> Operation.LESS_THAN_OR_EQUAL;
            default -> throw new UnknownFilterTypeException("Unknown filter type: " + type);
        };
        return SEARCH_FILTER_TRANSLATIONS.mapInto(jsonFilter, AttributeSearchFilter.builder())
                .operation(operation)
                .name(filterName)
                .attributePath(propertyPath)
                .flags(fromJsonSearchFilterFlags(jsonFilter.getFlags()))
                .build();
    }

    private List<SearchFilterFlag> fromJsonSearchFilterFlags(
            List<String> flags
    ) throws UnknownFlagException {
        if(flags == null) {
            return List.of();
        }
        List<SearchFilterFlag> result = new ArrayList<>();
        for (String flag : flags) {
            result.add(switch (flag) {
                case "hidden" -> HiddenSearchFilterFlag.INSTANCE;
                default -> throw new UnknownFlagException("Unknown flag '%s'".formatted(flag));
            });
        }
        return Collections.unmodifiableList(result);
    }

    private com.contentgrid.appserver.application.model.sortable.SortableField fromJsonSortableField(SortableField jsonSortableField)
            throws InvalidJsonException {
        var propertyPath = fromJsonPropertyPath(jsonSortableField.getAttributePath(), AttributePath.class);
        var sortableName = SortableName.of(jsonSortableField.getName());

        return com.contentgrid.appserver.application.model.sortable.SortableField.builder()
                .name(sortableName)
                .propertyPath(propertyPath)
                .build();
    }

    private com.contentgrid.appserver.application.model.relations.Relation fromJsonRelation(
            Relation jsonRelation) throws UnknownFlagException {
        var sourceEndPoint = fromJsonRelationEndPoint(jsonRelation.getSourceEndpoint());
        var targetEndPoint = fromJsonRelationEndPoint(jsonRelation.getTargetEndpoint());

        return switch (jsonRelation) {
            case OneToOneRelation oto -> SourceOneToOneRelation.builder()
                    .sourceEndPoint(sourceEndPoint)
                    .targetEndPoint(targetEndPoint)
                    .targetReference(ColumnName.of(oto.getTargetReference()))
                    .build();
            case OneToManyRelation otm ->
                    com.contentgrid.appserver.application.model.relations.OneToManyRelation.builder()
                            .sourceEndPoint(sourceEndPoint)
                            .targetEndPoint(targetEndPoint)
                            .sourceReference(ColumnName.of(otm.getSourceReference()))
                            .build();
            case ManyToManyRelation mtm ->
                    com.contentgrid.appserver.application.model.relations.ManyToManyRelation.builder()
                            .sourceEndPoint(sourceEndPoint)
                            .targetEndPoint(targetEndPoint)
                            .joinTable(TableName.of(mtm.getJoinTable()))
                            .sourceReference(ColumnName.of(mtm.getSourceReference()))
                            .targetReference(ColumnName.of(mtm.getTargetReference()))
                            .build();
        };
    }

    private com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint fromJsonRelationEndPoint(
            RelationEndPoint endPoint) throws UnknownFlagException {
        var entityName = EntityName.of(endPoint.getEntityName());
        var relationName = endPoint.getName() != null ? RelationName.of(endPoint.getName()) : null;
        var pathSegment = endPoint.getPathSegment() != null ? PathSegmentName.of(endPoint.getPathSegment()) : null;
        var linkName = endPoint.getLinkName() != null ? LinkName.of(endPoint.getLinkName()) : null;

        return RELATION_ENDPOINT_TRANSLATIONS.mapInto(endPoint, com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint.builder())
                .entity(entityName)
                .name(relationName)
                .pathSegment(pathSegment)
                .linkName(linkName)
                .flags(fromJsonRelationEndpointFlags(endPoint))
                .build();
    }

    private List<RelationEndpointFlag> fromJsonRelationEndpointFlags(RelationEndPoint endPoint) throws UnknownFlagException {
        List<RelationEndpointFlag> result = new ArrayList<>();
        for (String flag : Objects.requireNonNullElseGet(endPoint.getFlags(), List::<String>of)) {
            RelationEndpointFlag relationEndpointFlag = switch (flag) {
                case "hidden" -> HiddenEndpointFlag.INSTANCE;
                case "required" -> RequiredEndpointFlag.INSTANCE;
                default -> throw new UnknownFlagException("Unknown relation endpoint flag '%s'".formatted(flag));
            };
            result.add(relationEndpointFlag);
        }
        return Collections.unmodifiableList(result);
    }

    private EntityLink fromJsonEntityLink(com.contentgrid.appserver.application.model.json.model.EntityLink entityLink)
            throws InvalidJsonException {
        var identity =
                entityLink.getName() != null ? new LinkIdentity.NamedLink(entityLink.getRel(), entityLink.getName())
                        : new LinkIdentity.UnnamedLink(entityLink.getRel());

        UriTemplateDefinition templateDefinition = null;
        var fallbackTemplate = entityLink.getFallbackTemplate();
        if (fallbackTemplate != null) {
            var parser = new ParameterizedUriTemplateParser<>(EnumSet.allOf(UriTemplateDefinition.EntityLinkSubstitutionVariables.class));
            ParameterizedUriTemplate<EntityLinkSubstitutionVariables> parameterizedUriTemplate = null;
            try {
                parameterizedUriTemplate = parser.parse(fallbackTemplate.getTemplate());
            } catch (InvalidUriTemplateException e) {
                throw new InvalidEntityLinkTemplateException(e);
            }

            templateDefinition = entityLink.getFallbackTemplate().getAutomationSystem() != null ?
                    new UriTemplateDefinition.AutomationUriTemplateDefinition(
                            entityLink.getFallbackTemplate().getAutomationSystem(),
                            entityLink.getFallbackTemplate().getBasePathName(),
                            parameterizedUriTemplate
                    ) :
                    new UriTemplateDefinition.SimpleUriTemplateDefinition(parameterizedUriTemplate);
        }

        return new EntityLink(
                identity,
                entityLink.getProfile(),
                entityLink.getOwner() != null?fromJsonPropertyPath(entityLink.getOwner(), PropertyPath.class):null,
                entityLink.getStorage() != null?fromJsonPropertyPath(entityLink.getStorage(), AttributePath.class):null,
                templateDefinition
        );
    }


    /**
     * Converts an Application to its JSON representation and writes it to the given OutputStream.
     *
     * @param app the Application to convert
     * @param out the OutputStream to write the JSON to
     */
    @Override
    public void toJson(Application app, OutputStream out) {
        var schema = toJsonSchema(app);
        mapper.writeValue(out, schema);
    }

    // Reverse operation: Application -> ApplicationSchema
    private ApplicationSchema toJsonSchema(Application app) {
        var entities = app.getEntities().stream()
                .map(this::toJsonEntity)
                .toList();
        var relations = app.getRelations().stream()
                .map(this::toJsonRelation)
                .toList();
        var applicationSettings = this.toJsonApplicationSettings(app.getSettings());
        var schema = new ApplicationSchema();
        schema.setApplicationName(app.getName().getValue());
        schema.setEntities(entities);
        schema.setRelations(relations);
        schema.setSettings(applicationSettings);
        return schema;
    }

    private ApplicationSettings toJsonApplicationSettings(
            com.contentgrid.appserver.application.model.settings.ApplicationSettings applicationSettings) {
        var json = new ApplicationSettings();
        applicationSettings.getContentEncryption()
                .map(this::toJsonContentEncryptionSettings)
                .ifPresent(json::setContentEncryption);
        applicationSettings.getDatabase()
                .map(this::toJsonDatabaseSettings)
                .ifPresent(json::setDatabase);
        return json;
    }

    private ContentEncryptionSettings toJsonContentEncryptionSettings(
            com.contentgrid.appserver.application.model.settings.encryption.ContentEncryptionSettings settings) {
        var json = new ContentEncryptionSettings();
        json.setEnabled(settings.isEnabled());
        return json;
    }
     private DatabaseSettings toJsonDatabaseSettings(
             com.contentgrid.appserver.application.model.settings.database.DatabaseSettings settings) {
        var json = new DatabaseSettings();
        if (settings.getSchema() != null) {
            json.setSchema(settings.getSchema().getValue());
        }
        return json;
     }

    private Entity toJsonEntity(com.contentgrid.appserver.application.model.Entity entity) {
        var jsonEntity = new Entity();
        jsonEntity.setName(entity.getName().getValue());
        jsonEntity.setPathSegment(entity.getPathSegment().getValue());
        jsonEntity.setLinkName(entity.getLinkName().getValue());
        jsonEntity.setTitle(toJsonTranslations(entity, EntityTranslations::getSingularName).omitIfEqualTo(jsonEntity.getName()));
        jsonEntity.setCollectionTitle(toJsonTranslations(entity, EntityTranslations::getPluralName));
        jsonEntity.setDescription(toJsonTranslations(entity, EntityTranslations::getDescription));
        jsonEntity.setTable(entity.getTable().getValue());
        jsonEntity.setPrimaryKey((SimpleAttribute) toJsonAttribute(entity.getPrimaryKey()));
        jsonEntity.setAttributes(entity.getAttributes().stream().map(this::toJsonAttribute).toList());
        jsonEntity.setSearchFilters(entity.getSearchFilters().stream()
                .filter(searchfilter -> !searchfilter.hasFlag(SyntheticSearchFilterFlag.class))
                .map(this::toJsonSearchFilter).toList());
        jsonEntity.setSortableFields(entity.getSortableFields().stream().map(this::toJsonSortableField).toList());
        jsonEntity.setLinks(entity.getLinks().stream().map(this::toJsonEntityLink).toList());
        return jsonEntity;
    }

    private Attribute toJsonAttribute(com.contentgrid.appserver.application.model.attributes.Attribute attr) {
        var jsonAttr = switch (attr) {
            case com.contentgrid.appserver.application.model.attributes.SimpleAttribute sa -> toJsonSimpleAttribute(sa);
            case CompositeAttributeImpl ca -> toJsonCompositeAttribute(ca);
            case com.contentgrid.appserver.application.model.attributes.ContentAttribute ca ->
                    toJsonContentAttribute(ca);
            case com.contentgrid.appserver.application.model.attributes.UserAttribute ua -> toJsonUserAttribute(ua);
        };

        jsonAttr.setName(attr.getName().getValue());
        jsonAttr.setTitle(toJsonTranslations(attr, AttributeTranslations::getName).omitIfEqualTo(jsonAttr.getName()));
        jsonAttr.setDescription(toJsonTranslations(attr, AttributeTranslations::getDescription));

        return jsonAttr;
    }

    private SimpleAttribute toJsonSimpleAttribute(
            com.contentgrid.appserver.application.model.attributes.SimpleAttribute attr) {
        var jsonAttr = new SimpleAttribute();
        jsonAttr.setColumnName(attr.getColumn().getValue());
        jsonAttr.setDataType(attr.getType().name().toLowerCase());
        jsonAttr.setFlags(attr.getFlags().stream().map(this::toJsonAttribute).toList());
        jsonAttr.setConstraints(attr.getConstraints().stream().map(this::toJsonConstraint).toList());
        return jsonAttr;
    }

    private String toJsonAttribute(AttributeFlag flag) {
        return switch (flag) {
            case CreatedDateFlag ignored -> "createdDate";
            case CreatorFlag ignored -> "creator";
            case ETagFlag ignored -> "eTag";
            case ModifiedDateFlag ignored -> "modifiedDate";
            case ModifierFlag ignored -> "modifier";
            case IgnoredFlag ignored -> "ignored";
            case ReadOnlyFlag ignored -> "readOnly";
            default -> throw new IllegalArgumentException("Unknown flag: " + flag);
        };
    }

    private CompositeAttribute toJsonCompositeAttribute(CompositeAttributeImpl ca) {
        var jsonAttr = new CompositeAttribute();
        jsonAttr.setFlags(ca.getFlags().stream().map(this::toJsonAttribute).toList());
        jsonAttr.setAttributes(ca.getAttributes().stream().map(this::toJsonAttribute).toList());
        return jsonAttr;
    }

    private ContentAttribute toJsonContentAttribute(
            com.contentgrid.appserver.application.model.attributes.ContentAttribute ca) {
        var jsonAttr = new ContentAttribute();
        jsonAttr.setFlags(ca.getFlags().stream().map(this::toJsonAttribute).toList());
        jsonAttr.setPathSegment(ca.getPathSegment().getValue());
        jsonAttr.setLinkName(ca.getLinkName().getValue());
        jsonAttr.setIdColumn(ca.getId().getColumn().getValue());
        jsonAttr.setFileNameColumn(ca.getFilename().getColumn().getValue());
        jsonAttr.setMimeTypeColumn(ca.getMimetype().getColumn().getValue());
        jsonAttr.setLengthColumn(ca.getLength().getColumn().getValue());
        return jsonAttr;
    }

    private UserAttribute toJsonUserAttribute(com.contentgrid.appserver.application.model.attributes.UserAttribute ua) {
        var jsonAttr = new UserAttribute();
        jsonAttr.setFlags(ua.getFlags().stream().map(this::toJsonAttribute).toList());
        jsonAttr.setIdColumn(ua.getId().getColumn().getValue());
        jsonAttr.setNamespaceColumn(ua.getNamespace().getColumn().getValue());
        jsonAttr.setUserNameColumn(ua.getUsername().getColumn().getValue());
        return jsonAttr;
    }

    private AttributeConstraint toJsonConstraint(Constraint constraint) {
        return switch (constraint) {
            case Constraint.AllowedValuesConstraint allowedValuesConstraint -> {
                var avc = new AllowedValuesConstraint();
                avc.setValues(allowedValuesConstraint.getValues());
                yield avc;
            }
            case Constraint.UniqueConstraint ignored -> new UniqueConstraint();
            case Constraint.RequiredConstraint ignored -> new RequiredConstraint();
            case Constraint.RegexPatternConstraint regexPatternConstraint -> {
                var javaPattern = regexPatternConstraint.getPattern().pattern();
                var htmlPattern = regexPatternConstraint.getHtmlPattern();
                var pc = new PatternConstaint();
                pc.setRegex(javaPattern);
                // Canonical representation: htmlPattern is only present when it's not the same as the Java pattern
                if (!Objects.equals(javaPattern, htmlPattern)) {
                    pc.setHtmlPattern(htmlPattern);
                }
                yield  pc;
            }
        };
    }

    private SearchFilter toJsonSearchFilter(
            com.contentgrid.appserver.application.model.searchfilters.SearchFilter filter) {
        var jsonFilter = new SearchFilter();
        jsonFilter.setName(filter.getName().getValue());
        jsonFilter.setTitle(toJsonTranslations(filter, SearchFilterTranslations::getName).omitIfEqualTo(jsonFilter.getName()));
        jsonFilter.setDescription(toJsonTranslations(filter, SearchFilterTranslations::getDescription));
        jsonFilter.setFlags(toJsonSearchFilterFlags(filter.getFlags()));
        if (filter instanceof BaseAttributeSearchFilter baseAttributeSearchFilter) {
            jsonFilter.setAttributePath(toJsonPropertyPath(baseAttributeSearchFilter.getAttributePath()));

            String type;
            switch (filter) {
                case AttributeSearchFilter attributeSearchFilter ->
                    type = switch (attributeSearchFilter.getOperation()) {
                        case EXACT -> "exact";
                        case PREFIX -> "prefix";
                        case GREATER_THAN -> "greater";
                        case GREATER_THAN_OR_EQUAL -> "greater-or-equal";
                        case LESS_THAN -> "less";
                        case LESS_THAN_OR_EQUAL -> "less-or-equal";
                    };
                case FullTextSearchAttributeSearchFilter fullTextSearchAttributeSearchFilter -> {
                    type = FTS_TYPE;
                    jsonFilter.setLocale(fullTextSearchAttributeSearchFilter.getLocale());
                }
                default -> throw new IllegalStateException("Unexpected value: " + filter);
            }
            jsonFilter.setType(type);
        } else {
            throw new IllegalStateException("Unexpected value: " + filter);
        }
        return jsonFilter;
    }

    public List<String> toJsonSearchFilterFlags(Set<SearchFilterFlag> flags) {
        return flags.stream()
                .map(flag -> switch (flag) {
                    case HiddenSearchFilterFlag ignored -> "hidden";
                    default -> throw new IllegalArgumentException("Unknown flag: %s".formatted(flag));
                })
                .toList();
    }

    private SortableField toJsonSortableField(
            com.contentgrid.appserver.application.model.sortable.SortableField sortableField) {
        var jsonSortableField = new SortableField();
        jsonSortableField.setName(sortableField.getName().getValue());
        jsonSortableField.setAttributePath(toJsonPropertyPath(sortableField.getPropertyPath()));
        return jsonSortableField;
    }

    private com.contentgrid.appserver.application.model.json.model.EntityLink toJsonEntityLink(EntityLink entityLink) {
        var jsonEntityLink = new com.contentgrid.appserver.application.model.json.model.EntityLink();

        jsonEntityLink.setRel(entityLink.getIdentity().rel());
        if(entityLink.getIdentity() instanceof LinkIdentity.NamedLink namedLink) {
            jsonEntityLink.setName(namedLink.name());
        }

        jsonEntityLink.setProfile(entityLink.getProfile().orElse(null));

        entityLink.getOwner()
                .map(this::toJsonPropertyPath)
                .ifPresent(jsonEntityLink::setOwner);
        entityLink.getStorage()
                .map(this::toJsonPropertyPath)
                .ifPresent(jsonEntityLink::setStorage);

        switch (entityLink.getFallbackTemplate().orElse(null)) {
            case SimpleUriTemplateDefinition simple -> jsonEntityLink.setFallbackTemplate(new com.contentgrid.appserver.application.model.json.model.EntityLink.UriTemplateDefinition(null, null, simple.getTemplate().toTemplate()));
            case AutomationUriTemplateDefinition automation -> jsonEntityLink.setFallbackTemplate(new com.contentgrid.appserver.application.model.json.model.EntityLink.UriTemplateDefinition(automation.getAutomationSystem(), automation.getBasePathName(), automation.getTemplate().toTemplate()));
            case null -> jsonEntityLink.setFallbackTemplate(null);
        }

        return jsonEntityLink;
    }

    private List<PropertyPathElement> toJsonPropertyPath(PropertyPath propertyPath) {
        var result = new ArrayList<PropertyPathElement>();
        var path = propertyPath;

        while(path != null) {
            var element = path.getFirst();
            var jsonElement = new PropertyPathElement();
            jsonElement.setName(element.getValue());
            jsonElement.setType(switch(element) {
                case RelationName ignored -> PropertyPathElementType.RELATION;
                case AttributeName ignored -> PropertyPathElementType.ATTRIBUTE;
            });
            result.add(jsonElement);
            path = path.getRest();
        }

        return result;
    }

    private Relation toJsonRelation(com.contentgrid.appserver.application.model.relations.Relation relation) {
        return switch (relation) {
            case SourceOneToOneRelation oto -> {
                var json = new OneToOneRelation();
                setJsonRelationEndpoints(json, oto.getSourceEndPoint(), oto.getTargetEndPoint());
                json.setTargetReference(oto.getTargetReference().getValue());
                yield json;
            }
            case TargetOneToOneRelation toto -> toJsonRelation(toto.inverse());
            case com.contentgrid.appserver.application.model.relations.OneToManyRelation otm -> {
                var json = new OneToManyRelation();
                setJsonRelationEndpoints(json, otm.getSourceEndPoint(), otm.getTargetEndPoint());
                json.setSourceReference(otm.getSourceReference().getValue());
                yield json;
            }
            case ManyToOneRelation mto -> toJsonRelation(mto.inverse());
            case com.contentgrid.appserver.application.model.relations.ManyToManyRelation mtm -> {
                var json = new ManyToManyRelation();
                setJsonRelationEndpoints(json, mtm.getSourceEndPoint(), mtm.getTargetEndPoint());
                json.setJoinTable(mtm.getJoinTable().getValue());
                json.setSourceReference(mtm.getSourceReference().getValue());
                json.setTargetReference(mtm.getTargetReference().getValue());
                yield json;
            }
        };
    }

    private void setJsonRelationEndpoints(Relation json,
            com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint source,
            com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint target) {
        var sourceEp = toJsonRelationEndpoint(source);
        var targetEp = toJsonRelationEndpoint(target);
        json.setSourceEndpoint(sourceEp);
        json.setTargetEndpoint(targetEp);
    }

    private RelationEndPoint toJsonRelationEndpoint(
            com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint relationEndPoint) {
        var rep = new RelationEndPoint();
        rep.setEntityName(relationEndPoint.getEntity().getValue());
        rep.setName(relationEndPoint.getName() != null ? relationEndPoint.getName().getValue() : null);
        rep.setPathSegment(
                (relationEndPoint.getPathSegment() != null) ? relationEndPoint.getPathSegment().getValue() : null);
        rep.setLinkName(relationEndPoint.getLinkName() != null ? relationEndPoint.getLinkName().getValue() : null);
        rep.setTitle(toJsonTranslations(relationEndPoint, RelationEndPointTranslations::getName).omitIfEqualTo(rep.getName()));
        rep.setDescription(toJsonTranslations(relationEndPoint, RelationEndPointTranslations::getDescription));
        rep.setFlags(toJsonRelationEndpointFlags(relationEndPoint.getFlags()));
        return rep;
    }

    private List<String> toJsonRelationEndpointFlags(Set<RelationEndpointFlag> flags) {
        return flags.stream()
                .flatMap(flag -> switch (flag) {
                    case HiddenEndpointFlag ignored -> Stream.of("hidden");
                    case VisibleEndpointFlag ignored -> Stream.empty(); // Is just the implicit inverse of "HiddenEndpointFlag"
                    case RequiredEndpointFlag ignored -> Stream.of("required");
                    default -> throw new IllegalArgumentException("Unknown flag %s".formatted(flag));
        }).toList();

    }

    private <T> Translations toJsonTranslations(Translatable<T> translatable, Function<T, String> getter) {
        var translations = translatable.getTranslations().entrySet()
                .stream()
                .map(e -> {
                    var value = getter.apply(e.getValue());
                    if (value == null) {
                        return null;
                    }
                    return Map.entry(e.getKey(), value);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        if(translations.isEmpty()) {
            return EmptyTranslation.INSTANCE;
        }
        if(translations.size() == 1 && translations.containsKey(Locale.ROOT)) {
            return new SingleTranslation(translations.get(Locale.ROOT));
        }
        return new MultipleTranslations(translations);
    }
}
