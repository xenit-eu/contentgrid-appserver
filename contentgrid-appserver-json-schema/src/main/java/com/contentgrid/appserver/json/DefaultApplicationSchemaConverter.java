package com.contentgrid.appserver.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.json.model.ApplicationSchema;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.contentgrid.appserver.application.model.values.*;
import com.contentgrid.appserver.application.model.attributes.*;
import com.contentgrid.appserver.application.model.attributes.flags.*;
import com.contentgrid.appserver.application.model.searchfilters.*;
import com.contentgrid.appserver.application.model.Constraint;

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
                .name(com.contentgrid.appserver.application.model.values.ApplicationName.of(schema.getApplicationName()))
                .entities(entities)
                .relations(relations)
                .build();
    }

    private com.contentgrid.appserver.application.model.Entity convertEntity(com.contentgrid.appserver.json.model.Entity jsonEntity) {
        EntityName entityName = EntityName.of(jsonEntity.getName());
        PathSegmentName pathSegment = PathSegmentName.of(jsonEntity.getPathSegment());
        String description = jsonEntity.getDescription();
        TableName table = TableName.of(jsonEntity.getTable());
        SimpleAttribute primaryKey = convertSimpleAttribute(jsonEntity.getPrimaryKey());
        List<Attribute> attributes = jsonEntity.getAttributes() == null ? List.of() : jsonEntity.getAttributes().stream()
                .map(this::convertAttribute)
                .collect(Collectors.toList());
        List<SearchFilter> searchFilters = jsonEntity.getSearchFilters() == null ? List.of() : jsonEntity.getSearchFilters().stream()
                .map(sf -> convertSearchFilter(sf, attributes, primaryKey))
                .collect(Collectors.toList());
        return Entity.builder()
                .name(entityName)
                .pathSegment(pathSegment)
                .description(description)
                .table(table)
                .primaryKey(primaryKey)
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
        String name = jsonAttr.getName();
        String description = jsonAttr.getDescription();
        String column = jsonAttr.getColumnName();
        String dataType = jsonAttr.getDataType();
        Set<AttributeFlag> flags = convertFlags(jsonAttr.getFlags());
        List<Constraint> constraints = jsonAttr.getConstraints() == null ? List.of() : jsonAttr.getConstraints().stream()
                .map(this::convertConstraint)
                .collect(Collectors.toList());
        return SimpleAttribute.builder()
                .name(AttributeName.of(name))
                .description(description)
                .column(ColumnName.of(column))
                .type(SimpleAttribute.Type.valueOf(dataType.toUpperCase()))
                .flags(flags)
                .constraints(constraints)
                .build();
    }

    private CompositeAttribute convertCompositeAttribute(com.contentgrid.appserver.json.model.CompositeAttribute ca) {
        String name = ca.getName();
        String description = ca.getDescription();
        Set<AttributeFlag> flags = convertFlags(ca.getFlags());
        List<Attribute> attributes = ca.getAttributes().stream().map(this::convertAttribute).collect(Collectors.toList());
        return CompositeAttributeImpl.builder()
                .name(AttributeName.of(name))
                .description(description)
                .flags(flags)
                .attributes(attributes)
                .build();
    }

    private ContentAttribute convertContentAttribute(com.contentgrid.appserver.json.model.ContentAttribute ca) {
        String name = ca.getName();
        String description = ca.getDescription();
        Set<AttributeFlag> flags = convertFlags(ca.getFlags());
        String pathSegment = ca.getPathSegment();
        String idColumn = ca.getIdColumn();
        String fileNameColumn = ca.getFileNameColumn();
        String mimeTypeColumn = ca.getMimeTypeColumn();
        String lengthColumn = ca.getLengthColumn();
        return ContentAttribute.builder()
                .name(AttributeName.of(name))
                .description(description)
                .flags(flags)
                .pathSegment(PathSegmentName.of(pathSegment))
                .idColumn(ColumnName.of(idColumn))
                .filenameColumn(ColumnName.of(fileNameColumn))
                .mimetypeColumn(ColumnName.of(mimeTypeColumn))
                .lengthColumn(ColumnName.of(lengthColumn))
                .build();
    }

    private UserAttribute convertUserAttribute(com.contentgrid.appserver.json.model.UserAttribute ua) {
        String name = ua.getName();
        String description = ua.getDescription();
        Set<AttributeFlag> flags = convertFlags(ua.getFlags());
        String idColumn = ua.getIdColumn();
        String namespaceColumn = ua.getNamespaceColumn();
        String userNameColumn = ua.getUserNameColumn();
        return UserAttribute.builder()
                .name(AttributeName.of(name))
                .description(description)
                .flags(flags)
                .idColumn(ColumnName.of(idColumn))
                .namespaceColumn(ColumnName.of(namespaceColumn))
                .usernameColumn(ColumnName.of(userNameColumn))
                .build();
    }

    private Set<AttributeFlag> convertFlags(List<String> flags) {
        if (flags == null) return Set.of();
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

    private SearchFilter convertSearchFilter(com.contentgrid.appserver.json.model.SearchFilter jsonFilter, List<Attribute> attributes, SimpleAttribute primaryKey) {
        String type = jsonFilter.getType();
        String attrName = jsonFilter.getAttributeName();
        com.contentgrid.appserver.application.model.values.FilterName filterName = com.contentgrid.appserver.application.model.values.FilterName.of(jsonFilter.getName());
        SimpleAttribute attribute = (SimpleAttribute) attributes.stream()
                .filter(a -> a instanceof SimpleAttribute && ((SimpleAttribute) a).getName().getValue().equals(attrName))
                .findFirst()
                .orElse(primaryKey.getName().getValue().equals(attrName) ? primaryKey : null);
        if (attribute == null) throw new IllegalArgumentException("Attribute for filter not found: " + attrName);
        return switch (type) {
            case "prefix" -> PrefixSearchFilter.builder().name(filterName).attribute(attribute).build();
            case "exact" -> ExactSearchFilter.builder().name(filterName).attribute(attribute).build();
            default -> throw new IllegalArgumentException("Unknown filter type: " + type);
        };
    }

    private com.contentgrid.appserver.application.model.relations.Relation convertRelation(com.contentgrid.appserver.json.model.Relation jsonRelation, Set<com.contentgrid.appserver.application.model.Entity> entities) {
        java.util.function.Function<String, com.contentgrid.appserver.application.model.Entity> findEntity = name -> entities.stream()
                .filter(e -> e.getName().getValue().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + name));

        if (jsonRelation instanceof com.contentgrid.appserver.json.model.OneToOneRelation oto) {
            var sourceEp = oto.getSourceEndpoint();
            var targetEp = oto.getTargetEndpoint();
            var sourceEntity = findEntity.apply(sourceEp.getEntityName());
            var targetEntity = findEntity.apply(targetEp.getEntityName());
            var sourceName = sourceEp.getName() != null ? com.contentgrid.appserver.application.model.values.RelationName.of(sourceEp.getName()) : null;
            var targetName = targetEp.getName() != null ? com.contentgrid.appserver.application.model.values.RelationName.of(targetEp.getName()) : null;
            var sourcePath = sourceEp.getPathSegment() != null ? com.contentgrid.appserver.application.model.values.PathSegmentName.of(sourceEp.getPathSegment()) : null;
            var targetPath = targetEp.getPathSegment() != null ? com.contentgrid.appserver.application.model.values.PathSegmentName.of(targetEp.getPathSegment()) : null;
            boolean sourceRequired = sourceEp.isRequired();
            boolean targetRequired = targetEp.isRequired();
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

            return com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation.builder()
                    .sourceEndPoint(sourceEndPoint)
                    .targetEndPoint(targetEndPoint)
                    .targetReference(com.contentgrid.appserver.application.model.values.ColumnName.of(oto.getTargetReference()))
                    .build();
        } else if (jsonRelation instanceof com.contentgrid.appserver.json.model.OneToManyRelation otm) {
            var sourceEp = otm.getSourceEndpoint();
            var targetEp = otm.getTargetEndpoint();
            var sourceEntity = findEntity.apply(sourceEp.getEntityName());
            var targetEntity = findEntity.apply(targetEp.getEntityName());
            var sourceName = sourceEp.getName() != null ? com.contentgrid.appserver.application.model.values.RelationName.of(sourceEp.getName()) : null;
            var targetName = targetEp.getName() != null ? com.contentgrid.appserver.application.model.values.RelationName.of(targetEp.getName()) : null;
            var sourcePath = sourceEp.getPathSegment() != null ? com.contentgrid.appserver.application.model.values.PathSegmentName.of(sourceEp.getPathSegment()) : null;
            var targetPath = targetEp.getPathSegment() != null ? com.contentgrid.appserver.application.model.values.PathSegmentName.of(targetEp.getPathSegment()) : null;
            boolean sourceRequired = sourceEp.isRequired();
            boolean targetRequired = targetEp.isRequired();
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
            return com.contentgrid.appserver.application.model.relations.OneToManyRelation.builder()
                    .sourceEndPoint(sourceEndPoint)
                    .targetEndPoint(targetEndPoint)
                    .sourceReference(com.contentgrid.appserver.application.model.values.ColumnName.of(otm.getSourceReference()))
                    .build();
        } else if (jsonRelation instanceof com.contentgrid.appserver.json.model.ManyToManyRelation mtm) {
            var sourceEp = mtm.getSourceEndpoint();
            var targetEp = mtm.getTargetEndpoint();
            var sourceEntity = findEntity.apply(sourceEp.getEntityName());
            var targetEntity = findEntity.apply(targetEp.getEntityName());
            var sourceName = sourceEp.getName() != null ? com.contentgrid.appserver.application.model.values.RelationName.of(sourceEp.getName()) : null;
            var targetName = targetEp.getName() != null ? com.contentgrid.appserver.application.model.values.RelationName.of(targetEp.getName()) : null;
            var sourcePath = sourceEp.getPathSegment() != null ? com.contentgrid.appserver.application.model.values.PathSegmentName.of(sourceEp.getPathSegment()) : null;
            var targetPath = targetEp.getPathSegment() != null ? com.contentgrid.appserver.application.model.values.PathSegmentName.of(targetEp.getPathSegment()) : null;
            boolean sourceRequired = sourceEp.isRequired();
            boolean targetRequired = targetEp.isRequired();
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
            return com.contentgrid.appserver.application.model.relations.ManyToManyRelation.builder()
                    .sourceEndPoint(sourceEndPoint)
                    .targetEndPoint(targetEndPoint)
                    .joinTable(com.contentgrid.appserver.application.model.values.TableName.of(mtm.getJoinTable()))
                    .sourceReference(com.contentgrid.appserver.application.model.values.ColumnName.of(mtm.getSourceReference()))
                    .targetReference(com.contentgrid.appserver.application.model.values.ColumnName.of(mtm.getTargetReference()))
                    .build();
        } else {
            throw new IllegalArgumentException("Unknown relation type: " + jsonRelation.getClass());
        }
    }
}
