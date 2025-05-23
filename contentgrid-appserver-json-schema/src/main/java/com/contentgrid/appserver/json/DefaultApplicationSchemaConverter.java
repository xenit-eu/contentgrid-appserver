package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.json.exceptions.InValidJsonException;
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
import com.contentgrid.appserver.json.model.Relation;
import com.contentgrid.appserver.json.model.RelationEndPoint;
import com.contentgrid.appserver.json.model.RequiredConstraint;
import com.contentgrid.appserver.json.model.SearchFilter;
import com.contentgrid.appserver.json.model.SimpleAttribute;
import com.contentgrid.appserver.json.model.UniqueConstraint;
import com.contentgrid.appserver.json.model.UserAttribute;
import com.contentgrid.appserver.json.validation.ApplicationSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultApplicationSchemaConverter implements ApplicationSchemaConverter {

    private final ObjectMapper mapper = ApplicationSchemaObjectMapperFactory.createObjectMapper();
    private final ApplicationSchemaValidator validator = new ApplicationSchemaValidator();

    @Override
    public Application convert(InputStream json) throws InValidJsonException {
        var schema = getApplicationSchema(json);
        Set<com.contentgrid.appserver.application.model.Entity> entities = new HashSet<>();
        for (Entity entity : schema.getEntities()) {
            com.contentgrid.appserver.application.model.Entity convertEntity = convertEntity(entity);
            entities.add(convertEntity);
        }
        Set<com.contentgrid.appserver.application.model.relations.Relation> relations =
                schema.getRelations() == null ? Set.of() : schema.getRelations().stream()
                        .map(rel -> convertRelation(rel, entities))
                        .collect(Collectors.toSet());
        return Application.builder()
                .name(ApplicationName.of(
                        schema.getApplicationName()))
                .entities(entities)
                .relations(relations)
                .build();
    }

    private ApplicationSchema getApplicationSchema(InputStream json) throws InValidJsonException {
        try {
            var jsonString = new String(json.readAllBytes(), StandardCharsets.UTF_8);
            validator.validate(jsonString);
            return mapper.readValue(jsonString, ApplicationSchema.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private com.contentgrid.appserver.application.model.Entity convertEntity (
            Entity jsonEntity) throws InValidJsonException {
        com.contentgrid.appserver.application.model.attributes.SimpleAttribute primaryKey = convertSimpleAttribute(
                jsonEntity.getPrimaryKey());
        List<com.contentgrid.appserver.application.model.attributes.Attribute> attributes;
        if (jsonEntity.getAttributes() == null) {
            attributes = List.of();
        } else {
            attributes = convertAttributeList(jsonEntity.getAttributes());
        }
        List<com.contentgrid.appserver.application.model.searchfilters.SearchFilter> searchFilters =
                jsonEntity.getSearchFilters() == null ? List.of() : jsonEntity.getSearchFilters().stream()
                        .map(sf -> convertSearchFilter(sf, attributes, primaryKey))
                        .collect(Collectors.toList());
        return com.contentgrid.appserver.application.model.Entity.builder()
                .name(EntityName.of(jsonEntity.getName()))
                .pathSegment(PathSegmentName.of(jsonEntity.getPathSegment()))
                .description(jsonEntity.getDescription())
                .table(TableName.of(jsonEntity.getTable()))
                .primaryKey(convertSimpleAttribute(jsonEntity.getPrimaryKey()))
                .attributes(attributes)
                .searchFilters(searchFilters)
                .build();
    }

    private com.contentgrid.appserver.application.model.attributes.Attribute convertAttribute(Attribute jsonAttr)
            throws UnknownFlagException {
        return switch (jsonAttr) {
            case SimpleAttribute sa -> convertSimpleAttribute(sa);
            case CompositeAttribute ca -> convertCompositeAttribute(ca);
            case ContentAttribute ca -> convertContentAttribute(ca);
            case UserAttribute ua -> convertUserAttribute(ua);
        };
    }

    private com.contentgrid.appserver.application.model.attributes.SimpleAttribute convertSimpleAttribute(
            SimpleAttribute jsonAttr) throws UnknownFlagException {
        List<Constraint> constraints =
                jsonAttr.getConstraints() == null ? List.of() : jsonAttr.getConstraints().stream()
                        .map(this::convertConstraint)
                        .toList();
        return com.contentgrid.appserver.application.model.attributes.SimpleAttribute.builder()
                .name(AttributeName.of(jsonAttr.getName()))
                .description(jsonAttr.getDescription())
                .column(ColumnName.of(jsonAttr.getColumnName()))
                .type(Type.valueOf(jsonAttr.getDataType().toUpperCase()))
                .flags(convertFlags(jsonAttr.getFlags()))
                .constraints(constraints)
                .build();
    }

    private com.contentgrid.appserver.application.model.attributes.CompositeAttribute convertCompositeAttribute(
            CompositeAttribute ca) throws UnknownFlagException {
        return CompositeAttributeImpl.builder()
                .name(AttributeName.of(ca.getName()))
                .description(ca.getDescription())
                .flags(convertFlags(ca.getFlags()))
                .attributes(convertAttributeList(ca.getAttributes()))
                .build();
    }

    private List<com.contentgrid.appserver.application.model.attributes.Attribute> convertAttributeList(
            List<Attribute> attributes) throws UnknownFlagException {
        List<com.contentgrid.appserver.application.model.attributes.Attribute> list = new ArrayList<>();
        for (Attribute attribute : attributes) {
            com.contentgrid.appserver.application.model.attributes.Attribute convertAttribute = convertAttribute(
                    attribute);
            list.add(convertAttribute);
        }
        return list;
    }

    private com.contentgrid.appserver.application.model.attributes.ContentAttribute convertContentAttribute(
            ContentAttribute ca) throws UnknownFlagException {
        return com.contentgrid.appserver.application.model.attributes.ContentAttribute.builder()
                .name(AttributeName.of(ca.getName()))
                .description(ca.getDescription())
                .flags(convertFlags(ca.getFlags()))
                .pathSegment(PathSegmentName.of(ca.getPathSegment()))
                .idColumn(ColumnName.of(ca.getIdColumn()))
                .filenameColumn(ColumnName.of(ca.getFileNameColumn()))
                .mimetypeColumn(ColumnName.of(ca.getMimeTypeColumn()))
                .lengthColumn(ColumnName.of(ca.getLengthColumn()))
                .build();
    }

    private com.contentgrid.appserver.application.model.attributes.UserAttribute convertUserAttribute(
            UserAttribute ua) throws UnknownFlagException {
        return com.contentgrid.appserver.application.model.attributes.UserAttribute.builder()
                .name(AttributeName.of(ua.getName()))
                .description(ua.getDescription())
                .flags(convertFlags(ua.getFlags()))
                .idColumn(ColumnName.of(ua.getIdColumn()))
                .namespaceColumn(ColumnName.of(ua.getNamespaceColumn()))
                .usernameColumn(ColumnName.of(ua.getUserNameColumn()))
                .build();
    }

    private Set<AttributeFlag> convertFlags(List<String> flags) throws UnknownFlagException {
        if (flags == null) {
            return Set.of();
        }
        Set<AttributeFlag> set = new HashSet<>();
        for (String flag : flags) {
            AttributeFlag convertFlag = convertFlag(flag);
            set.add(convertFlag);
        }
        return set;
    }

    private AttributeFlag convertFlag(String flag) throws UnknownFlagException {
        return switch (flag) {
            case "createdDate" -> CreatedDateFlag.builder().build();
            case "creator" -> CreatorFlag.builder().build();
            case "eTag" -> ETagFlag.builder().build();
            case "modifiedDate" -> ModifiedDateFlag.builder().build();
            case "modifier" -> ModifierFlag.builder().build();
            default -> throw new UnknownFlagException("Unknown flag: " + flag);
        };
    }

    private Constraint convertConstraint(AttributeConstraint constraint) {
        return switch (constraint) {
            case AllowedValuesConstraint avc -> Constraint.allowedValues(avc.getValues());
            case UniqueConstraint ignored -> Constraint.unique();
            case RequiredConstraint ignored -> Constraint.required();
        };
    }

    private com.contentgrid.appserver.application.model.searchfilters.SearchFilter convertSearchFilter(
            SearchFilter jsonFilter,
            List<com.contentgrid.appserver.application.model.attributes.Attribute> attributes,
            com.contentgrid.appserver.application.model.attributes.SimpleAttribute primaryKey) {
        var type = jsonFilter.getType();
        var attrName = jsonFilter.getAttributeName();
        var filterName = FilterName.of(
                jsonFilter.getName());
        com.contentgrid.appserver.application.model.attributes.Attribute attribute = attributes.stream()
                .filter(a -> a.getName()
                        .getValue()
                        .equals(attrName))
                .findFirst()
                .orElse(primaryKey.getName().getValue().equals(attrName) ? primaryKey : null);
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute for filter not found: " + attrName);
        }
        if (!(attribute instanceof com.contentgrid.appserver.application.model.attributes.SimpleAttribute simpleAttribute)) {
            throw new IllegalArgumentException("Attribute for filter is not a simple attribute: " + attrName);
        } else {
            return switch (type) {
                case "prefix" -> PrefixSearchFilter.builder().name(filterName).attribute(simpleAttribute).build();
                case "exact" -> ExactSearchFilter.builder().name(filterName).attribute(simpleAttribute).build();
                default -> throw new IllegalArgumentException("Unknown filter type: " + type);
            };
        }
    }

    private com.contentgrid.appserver.application.model.Entity findEntity(String name,
            Set<com.contentgrid.appserver.application.model.Entity> entities) {
        return entities.stream()
                .filter(e -> e.getName().getValue().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + name));
    }

    private com.contentgrid.appserver.application.model.relations.Relation convertRelation(
            Relation jsonRelation,
            Set<com.contentgrid.appserver.application.model.Entity> entities) {
        var sourceEp = jsonRelation.getSourceEndpoint();
        var targetEp = jsonRelation.getTargetEndpoint();
        var sourceEntity = findEntity(sourceEp.getEntityName(), entities);
        var targetEntity = findEntity(targetEp.getEntityName(), entities);
        var sourceName =
                sourceEp.getName() != null ? RelationName.of(
                        sourceEp.getName()) : null;
        var targetName =
                targetEp.getName() != null ? RelationName.of(
                        targetEp.getName()) : null;
        var sourcePath = sourceEp.getPathSegment() != null
                ? PathSegmentName.of(sourceEp.getPathSegment())
                : null;
        var targetPath = targetEp.getPathSegment() != null
                ? PathSegmentName.of(targetEp.getPathSegment())
                : null;
        var sourceRequired = sourceEp.isRequired();
        var targetRequired = targetEp.isRequired();
        var sourceEndPoint = com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint.builder()
                .entity(sourceEntity)
                .name(sourceName)
                .pathSegment(sourcePath)
                .required(sourceRequired)
                .description(sourceEp.getDescription())
                .build();
        var targetEndPoint = com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint.builder()
                .entity(targetEntity)
                .name(targetName)
                .pathSegment(targetPath)
                .required(targetRequired)
                .description(targetEp.getDescription())
                .build();

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

    /**
     * Converts an Application to its JSON representation and writes it to the given OutputStream.
     *
     * @param app the Application to convert
     * @param out the OutputStream to write the JSON to
     */
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
        schema.setEntities(entities.stream().sorted(Comparator.comparing(Entity::getName)).toList());
        schema.setRelations(relations.stream()
                .sorted(Comparator.comparing(relation -> relation.getSourceEndpoint().getEntityName())).toList());
        return schema;
    }

    private Entity toJsonEntity(com.contentgrid.appserver.application.model.Entity entity) {
        var jsonEntity = new Entity();
        jsonEntity.setName(entity.getName().getValue());
        jsonEntity.setPathSegment(entity.getPathSegment().getValue());
        jsonEntity.setDescription(entity.getDescription());
        jsonEntity.setTable(entity.getTable().getValue());
        jsonEntity.setPrimaryKey(toJsonSimpleAttribute(entity.getPrimaryKey()));
        jsonEntity.setAttributes(entity.getAttributes().stream().map(this::toJsonAttribute)
                .sorted(Comparator.comparing(Attribute::getName)).toList());
        jsonEntity.setSearchFilters(entity.getSearchFilters().stream().map(this::toJsonSearchFilter).toList());
        return jsonEntity;
    }

    private Attribute toJsonAttribute(com.contentgrid.appserver.application.model.attributes.Attribute attr) {
        return switch (attr) {
            case com.contentgrid.appserver.application.model.attributes.SimpleAttribute sa -> toJsonSimpleAttribute(sa);
            case CompositeAttributeImpl ca -> toJsonCompositeAttribute(ca);
            case com.contentgrid.appserver.application.model.attributes.ContentAttribute ca ->
                    toJsonContentAttribute(ca);
            case com.contentgrid.appserver.application.model.attributes.UserAttribute ua -> toJsonUserAttribute(ua);
        };
    }

    private SimpleAttribute toJsonSimpleAttribute(
            com.contentgrid.appserver.application.model.attributes.SimpleAttribute attr) {
        var jsonAttr = new SimpleAttribute();
        jsonAttr.setName(attr.getName().getValue());
        jsonAttr.setDescription(attr.getDescription());
        jsonAttr.setColumnName(attr.getColumn().getValue());
        jsonAttr.setDataType(attr.getType().name().toLowerCase());
        jsonAttr.setFlags(attr.getFlags().stream().map(this::convertFlag).toList());
        jsonAttr.setConstraints(attr.getConstraints().stream().map(this::toJsonConstraint).toList());
        return jsonAttr;
    }

    private String convertFlag(AttributeFlag flag) {
        return switch (flag) {
            case CreatedDateFlag ignored -> "createdDate";
            case CreatorFlag ignored -> "creator";
            case ETagFlag ignored -> "eTag";
            case ModifiedDateFlag ignored -> "modifiedDate";
            case ModifierFlag ignored -> "modifier";
            default -> throw new IllegalArgumentException("Unknown flag: " + flag);
        };
    }

    private CompositeAttribute toJsonCompositeAttribute(CompositeAttributeImpl ca) {
        var jsonAttr = new CompositeAttribute();
        jsonAttr.setName(ca.getName().getValue());
        jsonAttr.setDescription(ca.getDescription());
        jsonAttr.setFlags(ca.getFlags().stream().map(this::convertFlag).toList());
        jsonAttr.setAttributes(ca.getAttributes().stream().map(this::toJsonAttribute).toList());
        return jsonAttr;
    }

    private ContentAttribute toJsonContentAttribute(
            com.contentgrid.appserver.application.model.attributes.ContentAttribute ca) {
        var jsonAttr = new ContentAttribute();
        jsonAttr.setName(ca.getName().getValue());
        jsonAttr.setDescription(ca.getDescription());
        jsonAttr.setFlags(ca.getFlags().stream().map(this::convertFlag).toList());
        jsonAttr.setPathSegment(ca.getPathSegment().getValue());
        jsonAttr.setIdColumn(ca.getId().getColumn().getValue());
        jsonAttr.setFileNameColumn(ca.getFilename().getColumn().getValue());
        jsonAttr.setMimeTypeColumn(ca.getMimetype().getColumn().getValue());
        jsonAttr.setLengthColumn(ca.getLength().getColumn().getValue());
        return jsonAttr;
    }

    private UserAttribute toJsonUserAttribute(com.contentgrid.appserver.application.model.attributes.UserAttribute ua) {
        var jsonAttr = new UserAttribute();
        jsonAttr.setName(ua.getName().getValue());
        jsonAttr.setDescription(ua.getDescription());
        jsonAttr.setFlags(ua.getFlags().stream().map(this::convertFlag).toList());
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
        switch (filter) {
            case PrefixSearchFilter prefixFilter -> {
                jsonFilter.setAttributeName(prefixFilter.getAttribute().getName().getValue());
                jsonFilter.setType("prefix");
            }
            case ExactSearchFilter exactFilter -> {
                jsonFilter.setAttributeName(exactFilter.getAttribute().getName().getValue());
                jsonFilter.setType("exact");
            }
            default -> throw new IllegalStateException("Unexpected value: " + filter);
        }
        return jsonFilter;
    }

    private Relation toJsonRelation(com.contentgrid.appserver.application.model.relations.Relation relation) {
        return switch (relation) {
            case SourceOneToOneRelation oto -> {
                var json = new OneToOneRelation();
                setRelationEndpoints(json, oto.getSourceEndPoint(), oto.getTargetEndPoint());
                json.setTargetReference(oto.getTargetReference().getValue());
                yield json;
            }
            case TargetOneToOneRelation toto -> {
                var json = new OneToOneRelation();
                setRelationEndpoints(json, toto.getTargetEndPoint(), toto.getSourceEndPoint());
                json.setTargetReference(toto.getSourceReference().getValue());
                yield json;
            }
            case com.contentgrid.appserver.application.model.relations.OneToManyRelation otm -> {
                var json = new OneToManyRelation();
                setRelationEndpoints(json, otm.getSourceEndPoint(), otm.getTargetEndPoint());
                json.setSourceReference(otm.getSourceReference().getValue());
                yield json;
            }
            case ManyToOneRelation mto -> {
                var json = new OneToManyRelation();
                setRelationEndpoints(json, mto.getTargetEndPoint(), mto.getSourceEndPoint());
                json.setSourceReference(mto.getTargetReference().getValue());
                yield json;
            }
            case com.contentgrid.appserver.application.model.relations.ManyToManyRelation mtm -> {
                var json = new ManyToManyRelation();
                setRelationEndpoints(json, mtm.getSourceEndPoint(), mtm.getTargetEndPoint());
                json.setJoinTable(mtm.getJoinTable().getValue());
                json.setSourceReference(mtm.getSourceReference().getValue());
                json.setTargetReference(mtm.getTargetReference().getValue());
                yield json;
            }
        };
    }

    private void setRelationEndpoints(Relation json,
            com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint source,
            com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint target) {
        var sourceEp = convertRelationEndPoint(source);
        var targetEp = convertRelationEndPoint(target);
        json.setSourceEndpoint(sourceEp);
        json.setTargetEndpoint(targetEp);
    }

    private RelationEndPoint convertRelationEndPoint(
            com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint relationEndPoint) {
        var rep = new RelationEndPoint();
        rep.setEntityName(relationEndPoint.getEntity().getName().getValue());
        rep.setName(relationEndPoint.getName() != null ? relationEndPoint.getName().getValue() : null);
        rep.setPathSegment(
                relationEndPoint.getPathSegment() != null ? relationEndPoint.getPathSegment().getValue() : null);
        rep.setRequired(relationEndPoint.isRequired());
        rep.setDescription(relationEndPoint.getDescription());
        return rep;
    }
}
