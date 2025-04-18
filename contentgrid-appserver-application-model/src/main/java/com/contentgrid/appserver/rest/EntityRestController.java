package com.contentgrid.appserver.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.EntityInstance;
import com.contentgrid.appserver.query.QueryEngine;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.EmbeddedWrappers;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EntityRestController {

    private final Application application;
    private final QueryEngine queryEngine;

    @GetMapping("/{entityName}")
    public CollectionModel<?> listEntity(@PathVariable PathSegmentName entityName, @RequestParam Map<String, String> params) {

        var maybeEntity = application.getEntityByPathSegment(entityName);

        if (maybeEntity.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var entity = maybeEntity.get();

        var results = queryEngine.query(entity, params);

        EmbeddedWrappers wrappers = new EmbeddedWrappers(false);
        var models = results.stream()
                .map(res -> wrappers.wrap(toRepresentationModel(entity, res), LinkRelation.of(entity.getName().getValue())))
                .toList();
        return CollectionModel.of(models);
    }

    @GetMapping("/{entityName}/{instanceId}")
    public RepresentationModel<?> getEntity(@PathVariable PathSegmentName entityName, @PathVariable String instanceId) {

        var maybeEntity = application.getEntityByPathSegment(entityName);
        if (maybeEntity.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var entity = maybeEntity.get();

        var result = queryEngine.findById(entity, instanceId);

        return result.map(res -> toRepresentationModel(entity, res))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    static RepresentationModel<?> toRepresentationModel(Entity entity, EntityInstance inst) {
        return RepresentationModel.of(inst)
                .add(linkTo(methodOn(EntityRestController.class)
                        .getEntity(entity.getPathSegment(), inst.getId())
                ).withSelfRel());
    }
}
