package com.contentgrid.appserver.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class RelationRestController {

    private final QueryEngine queryEngine;

    private Entity getEntityOrThrow(Application application, PathSegmentName entityName) {
        return application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    private Relation getRelationOrThrow(Application application, PathSegmentName entityName, PathSegmentName relationName) {
        return application.getRelationForPath(entityName, relationName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    @GetMapping("/{entityName}/{instanceId}/{relationName}")
    public ResponseEntity<?> followRelation(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName relationName
    ) {
        var relation = getRelationOrThrow(application, entityName, relationName);
        var targetPathSegment = relation.getTargetEndPoint().getEntity().getPathSegment();
        try {
            URI redirectUrl;
            switch (relation) {
                case OneToOneRelation oneToOneRelation -> {
                    var targetId = queryEngine.findTarget(application, oneToOneRelation, instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(relationName)));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).getEntity(application, targetPathSegment, targetId)).toUri();
                }
                case ManyToOneRelation manyToOneRelation -> {
                    var targetId = queryEngine.findTarget(application, manyToOneRelation, instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target of %s not found".formatted(relationName)));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).getEntity(application, targetPathSegment, targetId)).toUri();
                }
                case OneToManyRelation oneToManyRelation -> {
                    queryEngine.findById(application, oneToManyRelation.getSourceEndPoint().getEntity(), instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).listEntity(application, targetPathSegment, 0,
                            Map.of(relation.getTargetEndPoint().getName().getValue(), instanceId.toString()))).toUri(); // TODO: use RelationSearchFilter
                }
                case ManyToManyRelation manyToManyRelation -> {
                    queryEngine.findById(application, manyToManyRelation.getSourceEndPoint().getEntity(), instanceId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
                    redirectUrl = linkTo(methodOn(EntityRestController.class).listEntity(application, targetPathSegment, 0,
                            Map.of(relation.getTargetEndPoint().getName().getValue(), instanceId.toString()))).toUri(); // TODO: use RelationSearchFilter
                }
            }
            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
    }

}
