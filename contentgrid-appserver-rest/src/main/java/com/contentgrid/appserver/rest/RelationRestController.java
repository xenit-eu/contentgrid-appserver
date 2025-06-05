package com.contentgrid.appserver.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class RelationRestController {

    private final DatamodelApi datamodelApi;

    private Relation getRelationOrThrow(Application application, PathSegmentName entityName, PathSegmentName relationName) {
        return application.getRelationForPath(entityName, relationName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    @GetMapping("/{entityName}/{sourceId}/{relationName}")
    public ResponseEntity<Void> followRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId sourceId,
            @PathVariable PathSegmentName relationName
    ) {
        var relation = getRelationOrThrow(application, entityName, relationName);
        var targetPathSegment = relation.getTargetEndPoint().getEntity().getPathSegment();
        try {
            URI redirectUrl;
            switch (relation) {
                case OneToOneRelation oneToOneRelation -> {
                    var targetId = datamodelApi.findRelationTarget(application, oneToOneRelation, sourceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(relationName)));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).getEntity(application, targetPathSegment, targetId)).toUri();
                }
                case ManyToOneRelation manyToOneRelation -> {
                    var targetId = datamodelApi.findRelationTarget(application, manyToOneRelation, sourceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(relationName)));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).getEntity(application, targetPathSegment, targetId)).toUri();
                }
                case OneToManyRelation oneToManyRelation -> {
                    datamodelApi.findById(application, oneToManyRelation.getSourceEndPoint().getEntity(), sourceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id %s not found".formatted(sourceId)));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).listEntity(application, targetPathSegment, 0,
                            Map.of(relation.getTargetEndPoint().getName().getValue(), sourceId.toString()))).toUri(); // TODO: use RelationSearchFilter
                }
                case ManyToManyRelation manyToManyRelation -> {
                    datamodelApi.findById(application, manyToManyRelation.getSourceEndPoint().getEntity(), sourceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id %s not found".formatted(sourceId)));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).listEntity(application, targetPathSegment, 0,
                            Map.of(relation.getTargetEndPoint().getName().getValue(), sourceId.toString()))).toUri(); // TODO: use RelationSearchFilter
                }
            }
            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PutMapping(path = "/{entityName}/{sourceId}/{relationName}", consumes = "text/uri-list")
    public ResponseEntity<Void> setRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId sourceId,
            @PathVariable PathSegmentName relationName,
            @RequestBody List<URI> elements
    ) {
        if (elements.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No entity url provided.");
        }
        if (elements.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple targets not supported.");
        }
        var relation = getRelationOrThrow(application, entityName, relationName);
        var targetPathSegment = relation.getTargetEndPoint().getEntity().getPathSegment();

        if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple targets not supported.");
        }
        var element = elements.getFirst();

        var pathSegment = parseEntityPathSegment(element);
        if (!targetPathSegment.equals(pathSegment)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target entity. Expected %s, got %s.".formatted(targetPathSegment, pathSegment));
        }
        var targetId = parseEntityId(element);
        var data = XToOneRelationData.builder()
                .entity(relation.getSourceEndPoint().getEntity().getName())
                .name(relation.getSourceEndPoint().getName())
                .ref(targetId)
                .build();
        try {
            datamodelApi.setRelation(application, data, sourceId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    private PathSegmentName parseEntityPathSegment(URI uri) {
        var path = uri.getPath().split("/");
        if (path.length == 3 && path[0].isEmpty()) {
            return PathSegmentName.of(path[1]);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid path %s".formatted(uri.getPath()));
        }
    }

    private EntityId parseEntityId(URI uri) {
        var path = uri.getPath().split("/");
        if (path.length == 3 && path[0].isEmpty()) {
            return EntityId.of(UUID.fromString(path[2]));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid path %s".formatted(uri.getPath()));
        }
    }

    @PostMapping(path = "/{entityName}/{sourceId}/{relationName}", consumes = "text/uri-list")
    public ResponseEntity<Void> addRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId sourceId,
            @PathVariable PathSegmentName relationName,
            @RequestBody List<URI> elements
    ) {
        if (elements.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No entity url provided.");
        }
        var relation = getRelationOrThrow(application, entityName, relationName);
        var targetPathSegment = relation.getTargetEndPoint().getEntity().getPathSegment();

        if (!(relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Single targets not supported.");
        }

        var dataBuilder = XToManyRelationData.builder()
                .entity(relation.getSourceEndPoint().getEntity().getName())
                .name(relation.getSourceEndPoint().getName());

        for (var element : elements) {
            var pathSegment = parseEntityPathSegment(element);
            if (!targetPathSegment.equals(pathSegment)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid target entity. Expected %s, got %s.".formatted(targetPathSegment, pathSegment));
            }
            var targetId = parseEntityId(element);
            dataBuilder.ref(targetId);
        }
        try {
            datamodelApi.addRelationItems(application, dataBuilder.build(), sourceId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(path = "/{entityName}/{sourceId}/{relationName}")
    public ResponseEntity<Void> clearRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId sourceId,
            @PathVariable PathSegmentName relationName
    ) {
        var relation = getRelationOrThrow(application, entityName, relationName);

        try {
            datamodelApi.deleteRelation(application, relation, sourceId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(path = "/{entityName}/{sourceId}/{relationName}/{targetId}")
    public ResponseEntity<Void> removeRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId sourceId,
            @PathVariable PathSegmentName relationName,
            @PathVariable EntityId targetId
    ) {
        var relation = getRelationOrThrow(application, entityName, relationName);

        if (!(relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Single targets not supported.");
        }

        try {
            datamodelApi.removeRelationItem(application, relation, sourceId, targetId);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (ConstraintViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return ResponseEntity.noContent().build();
    }

}
