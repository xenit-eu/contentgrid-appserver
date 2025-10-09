package com.contentgrid.appserver.json;

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
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter.ConfigurableSearchFilterTranslations;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter.SearchFilterTranslations;
import com.contentgrid.appserver.application.model.searchfilters.flags.HiddenSearchFilterFlag;
import com.contentgrid.appserver.application.model.searchfilters.flags.SearchFilterFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.json.exceptions.InvalidJsonException;
import com.contentgrid.appserver.json.exceptions.UnknownFilterTypeException;
import com.contentgrid.appserver.json.exceptions.UnknownFlagException;
import com.contentgrid.appserver.json.model.AllowedValuesConstraint;
import com.contentgrid.appserver.json.model.ApplicationSchema;
import com.contentgrid.appserver.json.model.Attribute;
import com.contentgrid.appserver.json.model.AttributeConstraint;
import com.contentgrid.appserver.json.model.CompositeAttribute;
import com.contentgrid.appserver.json.model.ContentAttribute;
import com.contentgrid.appserver.json.model.Entity;
import com.contentgrid.appserver.json.model.ManyToManyRelation;
import com.contentgrid.appserver.json.model.OneToManyRelation;
import com.contentgrid.appserver.json.model.OneToOneRelation;
import com.contentgrid.appserver.json.model.PropertyPathElement;
import com.contentgrid.appserver.json.model.PropertyPathElement.PropertyPathElementType;
import com.contentgrid.appserver.json.model.Relation;
import com.contentgrid.appserver.json.model.RelationEndPoint;
import com.contentgrid.appserver.json.model.RequiredConstraint;
import com.contentgrid.appserver.json.model.SearchFilter;
import com.contentgrid.appserver.json.model.SimpleAttribute;
import com.contentgrid.appserver.json.model.SortableField;
import com.contentgrid.appserver.json.model.Translations;
import com.contentgrid.appserver.json.model.Translations.EmptyTranslation;
import com.contentgrid.appserver.json.model.UniqueConstraint;
import com.contentgrid.appserver.json.model.UserAttribute;
import com.contentgrid.appserver.json.validation.ApplicationSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

    private final ObjectMapper mapper = ApplicationSchemaObjectMapperFactory.createObjectMapper();
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
        Set<com.contentgrid.appserver.application.model.Entity> entities = new HashSet<>();
        for (Entity entity : schema.getEntities()) {
            com.contentgrid.appserver.application.model.Entity convertEntity = fromJsonEntity(entity);
            entities.add(convertEntity);
        }
        Set<com.contentgrid.appserver.application.model.relations.Relation> relations;
        if (schema.getRelations() == null) {
            relations = Set.of();
        } else {
            Set<com.contentgrid.appserver.application.model.relations.Relation> set = new HashSet<>();
            for (Relation rel : schema.getRelations()) {
                com.contentgrid.appserver.application.model.relations.Relation relation = fromJsonRelation(rel);
                set.add(relation);
            }
            relations = set;
        }
        return Application.builder()
                .name(ApplicationName.of(
                        schema.getApplicationName()))
                .entities(entities)
                .relations(relations)
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
        return ENTITY_TRANSLATIONS.mapInto(jsonEntity, com.contentgrid.appserver.application.model.Entity.builder())
                .name(EntityName.of(jsonEntity.getName()))
                .pathSegment(PathSegmentName.of(jsonEntity.getPathSegment()))
                .linkName(LinkName.of(jsonEntity.getLinkName()))
                .table(TableName.of(jsonEntity.getTable()))
                .primaryKey(primaryKey)
                .attributes(attributes)
                .searchFilters(searchFilters)
                .sortableFields(sortableFields)
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

    private Set<AttributeFlag> fromJsonAttributeFlags(List<String> flags) throws UnknownFlagException {
        if (flags == null) {
            return Set.of();
        }
        Set<AttributeFlag> set = new HashSet<>();
        for (String flag : flags) {
            AttributeFlag convertFlag = fromJsonAttributeFlag(flag);
            set.add(convertFlag);
        }
        return set;
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
        };
    }

    private com.contentgrid.appserver.application.model.searchfilters.SearchFilter fromJsonSearchFilter(
            SearchFilter jsonFilter
    ) throws InvalidJsonException {
        var type = jsonFilter.getType();
        List<PropertyName> attrPath = jsonFilter.getAttributePath().stream()
                .map(PropertyPathElement::toPropertyName)
                .toList();
        var propertyPath = PropertyPath.of(attrPath);
        var filterName = FilterName.of(jsonFilter.getName());

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

    private Set<SearchFilterFlag> fromJsonSearchFilterFlags(
            List<String> flags
    ) throws UnknownFlagException {
        if(flags == null) {
            return Set.of();
        }
        Set<SearchFilterFlag> set = new HashSet<>();
        for (String flag : flags) {
            set.add(switch (flag) {
                case "hidden" -> HiddenSearchFilterFlag.INSTANCE;
                default -> throw new UnknownFlagException("Unknown flag '%s'".formatted(flag));
            });
        }
        return Collections.unmodifiableSet(set);
    }

    private com.contentgrid.appserver.application.model.sortable.SortableField fromJsonSortableField(SortableField jsonSortableField) {
        List<PropertyName> attrPath = jsonSortableField.getAttributePath().stream()
                .map(PropertyPathElement::toPropertyName)
                .toList();
        var propertyPath = PropertyPath.of(attrPath);
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

    private Set<RelationEndpointFlag> fromJsonRelationEndpointFlags(RelationEndPoint endPoint) throws UnknownFlagException {
        Set<RelationEndpointFlag> set = new HashSet<>();
        for (String flag : Objects.requireNonNullElseGet(endPoint.getFlags(), List::<String>of)) {
            RelationEndpointFlag relationEndpointFlag = switch (flag) {
                case "hidden" -> HiddenEndpointFlag.INSTANCE;
                case "required" -> RequiredEndpointFlag.INSTANCE;
                default -> throw new UnknownFlagException("Unknown relation endpoint flag '%s'".formatted(flag));
            };
            set.add(relationEndpointFlag);
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * Converts an Application to its JSON representation and writes it to the given OutputStream.
     *
     * @param app the Application to convert
     * @param out the OutputStream to write the JSON to
     */
    @Override
    public void toJson(Application app, OutputStream out) {
        try {
            var schema = toJsonSchema(app);
            mapper.writeValue(out, schema);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Reverse operation: Application -> ApplicationSchema
    private ApplicationSchema toJsonSchema(Application app) {
        var entities = app.getEntities().stream()
                .map(this::toJsonEntity)
                .toList();
        var relations = app.getRelations().stream()
                .map(this::toJsonRelation)
                .toList();
        var schema = new ApplicationSchema();
        schema.setApplicationName(app.getName().getValue());
        schema.setEntities(entities);
        schema.setRelations(relations);
        return schema;
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
        jsonEntity.setSearchFilters(entity.getSearchFilters().stream().map(this::toJsonSearchFilter).toList());
        jsonEntity.setSortableFields(entity.getSortableFields().stream().map(this::toJsonSortableField).toList());
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
            case com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint allowedValuesConstraint -> {
                var avc = new AllowedValuesConstraint();
                avc.setValues(allowedValuesConstraint.getValues());
                yield avc;
            }
            case com.contentgrid.appserver.application.model.Constraint.UniqueConstraint ignored ->
                    new UniqueConstraint();
            case com.contentgrid.appserver.application.model.Constraint.RequiredConstraint ignored ->
                    new RequiredConstraint();
        };
    }

    private SearchFilter toJsonSearchFilter(
            com.contentgrid.appserver.application.model.searchfilters.SearchFilter filter) {
        var jsonFilter = new SearchFilter();
        jsonFilter.setName(filter.getName().getValue());
        jsonFilter.setTitle(toJsonTranslations(filter, SearchFilterTranslations::getName).omitIfEqualTo(jsonFilter.getName()));
        jsonFilter.setDescription(toJsonTranslations(filter, SearchFilterTranslations::getDescription));
        jsonFilter.setFlags(toJsonSearchFilterFlags(filter.getFlags()));
        if (filter instanceof AttributeSearchFilter attributeFilter) {
            jsonFilter.setAttributePath(toJsonPropertyPath(attributeFilter.getAttributePath()));
            var type = switch (attributeFilter.getOperation()) {
                case EXACT -> "exact";
                case PREFIX -> "prefix";
                case FTS -> "fts";
                case GREATER_THAN -> "greater";
                case GREATER_THAN_OR_EQUAL -> "greater-or-equal";
                case LESS_THAN -> "less";
                case LESS_THAN_OR_EQUAL -> "less-or-equal";
            };
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
            return new Translations.SingleTranslation(translations.get(Locale.ROOT));
        }
        return new Translations.MultipleTranslations(translations);
    }
}
