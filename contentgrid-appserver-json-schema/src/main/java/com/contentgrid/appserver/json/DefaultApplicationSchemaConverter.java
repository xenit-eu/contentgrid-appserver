package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.AttributeFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.json.model.ApplicationSchema;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultApplicationSchemaConverter implements ApplicationSchemaConverter {

    @Override
    public Application convert(ApplicationSchema schema) {
        Set<Entity> entities = schema.getEntities().stream()
                .map(this::convertEntity)
                .collect(Collectors.toSet());
        Set<Relation> relations = schema.getRelations() == null ? Set.of() : schema.getRelations().stream()
                .map(rel -> convertRelation(rel, entities))
                .collect(Collectors.toSet());
        return Application.builder()
                .name(com.contentgrid.appserver.application.model.values.ApplicationName.of(
                        schema.getApplicationName()))
                .entities(entities)
                .relations(relations)
                .build();
    }

    private com.contentgrid.appserver.application.model.Entity convertEntity(
            com.contentgrid.appserver.json.model.Entity jsonEntity) {
        SimpleAttribute primaryKey = convertSimpleAttribute(jsonEntity.getPrimaryKey());
        List<Attribute> attributes =
                jsonEntity.getAttributes() == null ? List.of() : jsonEntity.getAttributes().stream()
                        .map(this::convertAttribute)
                        .collect(Collectors.toList());
        List<SearchFilter> searchFilters =
                jsonEntity.getSearchFilters() == null ? List.of() : jsonEntity.getSearchFilters().stream()
                        .map(sf -> convertSearchFilter(sf, attributes, primaryKey))
                        .collect(Collectors.toList());
        return Entity.builder()
                .name(EntityName.of(jsonEntity.getName()))
                .pathSegment(PathSegmentName.of(jsonEntity.getPathSegment()))
                .description(jsonEntity.getDescription())
                .table(TableName.of(jsonEntity.getTable()))
                .primaryKey(convertSimpleAttribute(jsonEntity.getPrimaryKey()))
                .attributes(attributes)
                .searchFilters(searchFilters)
                .build();
    }

    private Attribute convertAttribute(com.contentgrid.appserver.json.model.Attribute jsonAttr) {
        if (jsonAttr instanceof com.contentgrid.appserver.json.model.SimpleAttribute sa) {
            return convertSimpleAttribute(sa);
        } else if (jsonAttr instanceof com.contentgrid.appserver.json.model.CompositeAttribute ca) {
            return convertCompositeAttribute(ca);
        } else if (jsonAttr instanceof com.contentgrid.appserver.json.model.ContentAttribute ca) {
            return convertContentAttribute(ca);
        } else if (jsonAttr instanceof com.contentgrid.appserver.json.model.UserAttribute ua) {
            return convertUserAttribute(ua);
        }
        throw new IllegalArgumentException("Unknown attribute type: " + jsonAttr.getClass());
    }

    private SimpleAttribute convertSimpleAttribute(com.contentgrid.appserver.json.model.SimpleAttribute jsonAttr) {
        List<Constraint> constraints =
                jsonAttr.getConstraints() == null ? List.of() : jsonAttr.getConstraints().stream()
                        .map(this::convertConstraint)
                        .toList();
        return SimpleAttribute.builder()
                .name(AttributeName.of(jsonAttr.getName()))
                .description(jsonAttr.getDescription())
                .column(ColumnName.of(jsonAttr.getColumnName()))
                .type(SimpleAttribute.Type.valueOf(jsonAttr.getDataType().toUpperCase()))
                .flags(convertFlags(jsonAttr.getFlags()))
                .constraints(constraints)
                .build();
    }

    private CompositeAttribute convertCompositeAttribute(com.contentgrid.appserver.json.model.CompositeAttribute ca) {
        return CompositeAttributeImpl.builder()
                .name(AttributeName.of(ca.getName()))
                .description(ca.getDescription())
                .flags(convertFlags(ca.getFlags()))
                .attributes(ca.getAttributes().stream().map(this::convertAttribute).toList())
                .build();
    }

    private ContentAttribute convertContentAttribute(com.contentgrid.appserver.json.model.ContentAttribute ca) {
        return ContentAttribute.builder()
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

    private UserAttribute convertUserAttribute(com.contentgrid.appserver.json.model.UserAttribute ua) {
        return UserAttribute.builder()
                .name(AttributeName.of(ua.getName()))
                .description(ua.getDescription())
                .flags(convertFlags(ua.getFlags()))
                .idColumn(ColumnName.of(ua.getIdColumn()))
                .namespaceColumn(ColumnName.of(ua.getNamespaceColumn()))
                .usernameColumn(ColumnName.of(ua.getUserNameColumn()))
                .build();
    }

    private Set<AttributeFlag> convertFlags(List<String> flags) {
        if (flags == null)
            return Set.of();
        return flags.stream().map(this::convertFlag).collect(Collectors.toSet());
    }

    private AttributeFlag convertFlag(String flag) {
        return switch (flag) {
            case "createdDate" -> CreatedDateFlag.builder().build();
            case "creator" -> CreatorFlag.builder().build();
            case "eTag" -> ETagFlag.builder().build();
            case "modifiedDate" -> ModifiedDateFlag.builder().build();
            case "modifier" -> ModifierFlag.builder().build();
            default -> throw new IllegalArgumentException("Unknown flag: " + flag);
        };
    }

    private Constraint convertConstraint(com.contentgrid.appserver.json.model.AttributeConstraint constraint) {
        if (constraint instanceof com.contentgrid.appserver.json.model.AllowedValuesConstraint avc) {
            return Constraint.allowedValues(avc.getValues());
        } else if (constraint instanceof com.contentgrid.appserver.json.model.UniqueConstraint) {
            return Constraint.unique();
        } else if (constraint instanceof com.contentgrid.appserver.json.model.RequiredConstraint) {
            return Constraint.required();
        }
        throw new IllegalArgumentException("Unknown constraint type: " + constraint.getClass());
    }

    private SearchFilter convertSearchFilter(com.contentgrid.appserver.json.model.SearchFilter jsonFilter,
            List<Attribute> attributes, SimpleAttribute primaryKey) {
        String type = jsonFilter.getType();
        String attrName = jsonFilter.getAttributeName();
        com.contentgrid.appserver.application.model.values.FilterName filterName = com.contentgrid.appserver.application.model.values.FilterName.of(
                jsonFilter.getName());
        SimpleAttribute attribute = (SimpleAttribute) attributes.stream()
                .filter(a -> a instanceof SimpleAttribute && ((SimpleAttribute) a).getName().getValue()
                        .equals(attrName))
                .findFirst()
                .orElse(primaryKey.getName().getValue().equals(attrName) ? primaryKey : null);
        if (attribute == null)
            throw new IllegalArgumentException("Attribute for filter not found: " + attrName);
        return switch (type) {
            case "prefix" -> PrefixSearchFilter.builder().name(filterName).attribute(attribute).build();
            case "exact" -> ExactSearchFilter.builder().name(filterName).attribute(attribute).build();
            default -> throw new IllegalArgumentException("Unknown filter type: " + type);
        };
    }

    private Entity findEntity(String name, Set<Entity> entities) {
        return entities.stream()
                .filter(e -> e.getName().getValue().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + name));
    }

    private com.contentgrid.appserver.application.model.relations.Relation convertRelation(
            com.contentgrid.appserver.json.model.Relation jsonRelation,
            Set<Entity> entities) {
        var sourceEp = jsonRelation.getSourceEndpoint();
        var targetEp = jsonRelation.getTargetEndpoint();
        var sourceEntity = findEntity(sourceEp.getEntityName(), entities);
        var targetEntity = findEntity(targetEp.getEntityName(), entities);
        var sourceName =
                sourceEp.getName() != null ? com.contentgrid.appserver.application.model.values.RelationName.of(
                        sourceEp.getName()) : null;
        var targetName =
                targetEp.getName() != null ? com.contentgrid.appserver.application.model.values.RelationName.of(
                        targetEp.getName()) : null;
        var sourcePath = sourceEp.getPathSegment() != null
                ? com.contentgrid.appserver.application.model.values.PathSegmentName.of(sourceEp.getPathSegment())
                : null;
        var targetPath = targetEp.getPathSegment() != null
                ? com.contentgrid.appserver.application.model.values.PathSegmentName.of(targetEp.getPathSegment())
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
            case com.contentgrid.appserver.json.model.OneToOneRelation oto ->
                    com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation.builder()
                            .sourceEndPoint(sourceEndPoint)
                            .targetEndPoint(targetEndPoint)
                            .targetReference(ColumnName.of(oto.getTargetReference()))
                            .build();
            case com.contentgrid.appserver.json.model.OneToManyRelation otm ->
                    com.contentgrid.appserver.application.model.relations.OneToManyRelation.builder()
                            .sourceEndPoint(sourceEndPoint)
                            .targetEndPoint(targetEndPoint)
                            .sourceReference(ColumnName.of(otm.getSourceReference()))
                            .build();
            case com.contentgrid.appserver.json.model.ManyToManyRelation mtm ->
                    com.contentgrid.appserver.application.model.relations.ManyToManyRelation.builder()
                            .sourceEndPoint(sourceEndPoint)
                            .targetEndPoint(targetEndPoint)
                            .joinTable(TableName.of(mtm.getJoinTable()))
                            .sourceReference(ColumnName.of(mtm.getSourceReference()))
                            .targetReference(ColumnName.of(mtm.getTargetReference()))
                            .build();
        };
    }
}
