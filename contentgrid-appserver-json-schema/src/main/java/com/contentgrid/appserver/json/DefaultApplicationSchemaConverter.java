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
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
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
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.json.exceptions.AttributeNotFoundException;
import com.contentgrid.appserver.json.exceptions.InValidJsonException;
import com.contentgrid.appserver.json.exceptions.InvalidAttributeTypeException;
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
import java.util.stream.Stream;

public class DefaultApplicationSchemaConverter implements ApplicationSchemaConverter {

    private final ObjectMapper mapper = ApplicationSchemaObjectMapperFactory.createObjectMapper();
    private final ApplicationSchemaValidator validator = new ApplicationSchemaValidator();

    @Override
    public Application convert(InputStream json) throws InValidJsonException {
        var schema = getApplicationSchema(json);
        Set<com.contentgrid.appserver.application.model.Entity> entities = new HashSet<>();
        for (Entity entity : schema.getEntities()) {
            com.contentgrid.appserver.application.model.Entity convertEntity = fromJsonEntity(entity,
                    schema.getEntities(), schema.getRelations());
            entities.add(convertEntity);
        }
        Set<com.contentgrid.appserver.application.model.relations.Relation> relations =
                schema.getRelations() == null ? Set.of() : schema.getRelations().stream()
                        .map(rel -> fromJsonRelation(rel, entities))
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

    private com.contentgrid.appserver.application.model.Entity fromJsonEntity(
            Entity jsonEntity, List<Entity> jsonEntities, List<Relation> jsonRelations) throws InValidJsonException {
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
                com.contentgrid.appserver.application.model.searchfilters.SearchFilter searchFilter = fromJsonSearchFilter(
                        sf, jsonEntity, jsonEntities, jsonRelations);
                list.add(searchFilter);
            }
            searchFilters = list;
        }
        return com.contentgrid.appserver.application.model.Entity.builder()
                .name(EntityName.of(jsonEntity.getName()))
                .pathSegment(PathSegmentName.of(jsonEntity.getPathSegment()))
                .linkName(LinkName.of(jsonEntity.getLinkName()))
                .description(jsonEntity.getDescription())
                .table(TableName.of(jsonEntity.getTable()))
                .primaryKey(primaryKey)
                .attributes(attributes)
                .searchFilters(searchFilters)
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
        return com.contentgrid.appserver.application.model.attributes.SimpleAttribute.builder()
                .name(AttributeName.of(jsonAttr.getName()))
                .description(jsonAttr.getDescription())
                .column(ColumnName.of(jsonAttr.getColumnName()))
                .type(Type.valueOf(jsonAttr.getDataType().toUpperCase()))
                .flags(fromJsonFlags(jsonAttr.getFlags()))
                .constraints(constraints)
                .build();
    }

    private com.contentgrid.appserver.application.model.attributes.CompositeAttribute fromJsonCompositeAttribute(
            CompositeAttribute ca) throws UnknownFlagException {
        return CompositeAttributeImpl.builder()
                .name(AttributeName.of(ca.getName()))
                .description(ca.getDescription())
                .flags(fromJsonFlags(ca.getFlags()))
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
        return com.contentgrid.appserver.application.model.attributes.ContentAttribute.builder()
                .name(AttributeName.of(ca.getName()))
                .description(ca.getDescription())
                .flags(fromJsonFlags(ca.getFlags()))
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
        return com.contentgrid.appserver.application.model.attributes.UserAttribute.builder()
                .name(AttributeName.of(ua.getName()))
                .description(ua.getDescription())
                .flags(fromJsonFlags(ua.getFlags()))
                .idColumn(ColumnName.of(ua.getIdColumn()))
                .namespaceColumn(ColumnName.of(ua.getNamespaceColumn()))
                .usernameColumn(ColumnName.of(ua.getUserNameColumn()))
                .build();
    }

    private Set<AttributeFlag> fromJsonFlags(List<String> flags) throws UnknownFlagException {
        if (flags == null) {
            return Set.of();
        }
        Set<AttributeFlag> set = new HashSet<>();
        for (String flag : flags) {
            AttributeFlag convertFlag = fromJsonFlag(flag);
            set.add(convertFlag);
        }
        return set;
    }

    private AttributeFlag fromJsonFlag(String flag) throws UnknownFlagException {
        return switch (flag) {
            case "createdDate" -> CreatedDateFlag.builder().build();
            case "creator" -> CreatorFlag.builder().build();
            case "eTag" -> ETagFlag.builder().build();
            case "modifiedDate" -> ModifiedDateFlag.builder().build();
            case "modifier" -> ModifierFlag.builder().build();
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
            SearchFilter jsonFilter,
            Entity jsonEntity,
            List<Entity> jsonEntities,
            List<Relation> jsonRelations
    ) throws InValidJsonException {
        var type = jsonFilter.getType();
        List<PropertyName> attrPath = jsonFilter.getAttributePath().stream()
                .map(element -> element.getType().equals("attr")
                        ? AttributeName.of(element.getName())
                        : RelationName.of(element.getName())
                ).toList();
        var propertyPath = PropertyPath.of(attrPath);
        var filterName = FilterName.of(jsonFilter.getName());

        // Finding the type of the attribute pointed to by the path, traversing the path if there's multiple elements
        var currentContainer = jsonEntity.getAttributes();
        var currentEntity = jsonEntity;
        Attribute attribute = null;
        for (PropertyName prop : attrPath) {
            var propName = prop.getValue();
            var entityName = currentEntity.getName();
            var attr = currentContainer.stream().filter(a -> a.getName().equals(propName)).findFirst().orElse(null);
            // If our entity name matches one of the endpoints of a relation, we need the other endpoint to actually
            // follow the relation to the next entity.
            // Turn every relation into 2 pairs of endpoints, source to target and target to source
            var rel = jsonRelations.stream().flatMap(r -> Stream.of(
                            new Endpoints(r.getSourceEndpoint(), r.getTargetEndpoint()),
                            new Endpoints(r.getTargetEndpoint(), r.getSourceEndpoint())
                    ))
                    .filter(e -> propName.equals(e.source().getName()) && e.source().getEntityName().equals(entityName))
                    .map(Endpoints::target)
                    .findFirst().orElse(null);
            if (attr == null && rel == null) {
                throw new InvalidSearchFilterException("Attribute or relation for filter not found: " + propName);
            } else if (attr != null) {
                if (attr instanceof CompositeAttribute composite) {
                    currentContainer = composite.getAttributes();
                } else {
                    attribute = attr;
                }
            } else {
                currentEntity = jsonEntities.stream().filter(e -> e.getName().equals(rel.getEntityName()))
                        .findFirst().orElseThrow(() -> new InvalidSearchFilterException("Entity from path not found"));
                currentContainer = currentEntity.getAttributes();
            }

        }

        if (attribute == null) {
            throw new InvalidSearchFilterException("Attribute for filter not found: " + propertyPath.toList());
        }

        var targetAttribute = fromJsonAttribute(attribute);

        if (!(targetAttribute instanceof com.contentgrid.appserver.application.model.attributes.SimpleAttribute simpleAttribute)) {
            throw new InvalidSearchFilterException("Attribute for filter is not a simple attribute: " + attribute.getName());
        } else {
            return switch (type) {
                case "prefix" -> PrefixSearchFilter.builder().name(filterName).attributePath(propertyPath).attributeType(simpleAttribute.getType()).build();
                case "exact" -> ExactSearchFilter.builder().name(filterName).attributePath(propertyPath).attributeType(simpleAttribute.getType()).build();
                default -> throw new UnknownFilterTypeException("Unknown filter type: " + type);
            };
        }
    }

    private record Endpoints(RelationEndPoint source, RelationEndPoint target) {}

    private com.contentgrid.appserver.application.model.Entity findEntity(String name,
            Set<com.contentgrid.appserver.application.model.Entity> entities) {
        return entities.stream()
                .filter(e -> e.getName().getValue().equals(name))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Entity not found: " + name));
    }

    private com.contentgrid.appserver.application.model.relations.Relation fromJsonRelation(
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
        var sourceLink = sourceEp.getLinkName() != null ? LinkName.of(sourceEp.getLinkName()) : null;
        var targetLink = targetEp.getLinkName() != null ? LinkName.of(targetEp.getLinkName()) : null;
        var sourceRequired = sourceEp.isRequired();
        var targetRequired = targetEp.isRequired();
        var sourceEndPoint = com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint.builder()
                .entity(sourceEntity)
                .name(sourceName)
                .pathSegment(sourcePath)
                .linkName(sourceLink)
                .required(sourceRequired)
                .description(sourceEp.getDescription())
                .build();
        var targetEndPoint = com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint.builder()
                .entity(targetEntity)
                .name(targetName)
                .pathSegment(targetPath)
                .linkName(targetLink)
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
        schema.setEntities(entities.stream().sorted(Comparator.comparing(Entity::getName)).toList());
        schema.setRelations(relations.stream()
                .sorted(Comparator.comparing(relation -> relation.getSourceEndpoint().getEntityName())).toList());
        return schema;
    }

    private Entity toJsonEntity(com.contentgrid.appserver.application.model.Entity entity) {
        var jsonEntity = new Entity();
        jsonEntity.setName(entity.getName().getValue());
        jsonEntity.setPathSegment(entity.getPathSegment().getValue());
        jsonEntity.setLinkName(entity.getLinkName().getValue());
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
            default -> throw new IllegalArgumentException("Unknown flag: " + flag);
        };
    }

    private CompositeAttribute toJsonCompositeAttribute(CompositeAttributeImpl ca) {
        var jsonAttr = new CompositeAttribute();
        jsonAttr.setName(ca.getName().getValue());
        jsonAttr.setDescription(ca.getDescription());
        jsonAttr.setFlags(ca.getFlags().stream().map(this::toJsonAttribute).toList());
        jsonAttr.setAttributes(ca.getAttributes().stream().map(this::toJsonAttribute).toList());
        return jsonAttr;
    }

    private ContentAttribute toJsonContentAttribute(
            com.contentgrid.appserver.application.model.attributes.ContentAttribute ca) {
        var jsonAttr = new ContentAttribute();
        jsonAttr.setName(ca.getName().getValue());
        jsonAttr.setDescription(ca.getDescription());
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
        jsonAttr.setName(ua.getName().getValue());
        jsonAttr.setDescription(ua.getDescription());
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
        switch (filter) {
            case PrefixSearchFilter prefixFilter -> {
                jsonFilter.setAttributePath(toJsonPropertyPath(prefixFilter.getAttributePath()));
                jsonFilter.setType("prefix");
            }
            case ExactSearchFilter exactFilter -> {
                jsonFilter.setAttributePath(toJsonPropertyPath(exactFilter.getAttributePath()));
                jsonFilter.setType("exact");
            }
            default -> throw new IllegalStateException("Unexpected value: " + filter);
        }
        return jsonFilter;
    }

    private List<PropertyPathElement> toJsonPropertyPath(PropertyPath propertyPath) {
        var result = new ArrayList<PropertyPathElement>();
        var path = propertyPath;

        while(path != null) {
            var element = path.getFirst();
            var jsonElement = new PropertyPathElement();
            jsonElement.setName(element.getValue());
            jsonElement.setType(switch(element) {
                case RelationName ignored -> "rel";
                case AttributeName ignored -> "attr";
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
        rep.setEntityName(relationEndPoint.getEntity().getName().getValue());
        rep.setName(relationEndPoint.getName() != null ? relationEndPoint.getName().getValue() : null);
        rep.setPathSegment(
                relationEndPoint.getPathSegment() != null ? relationEndPoint.getPathSegment().getValue() : null);
        rep.setLinkName(relationEndPoint.getLinkName() != null ? relationEndPoint.getLinkName().getValue() : null);
        rep.setRequired(relationEndPoint.isRequired());
        rep.setDescription(relationEndPoint.getDescription());
        return rep;
    }
}
