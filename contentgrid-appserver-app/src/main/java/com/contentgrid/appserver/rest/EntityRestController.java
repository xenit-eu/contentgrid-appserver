package com.contentgrid.appserver.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.query.EntityInstance;
import com.contentgrid.appserver.query.PageRequest;
import com.contentgrid.appserver.query.QueryEngine;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.PagedModel.PageMetadata;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.EmbeddedWrappers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EntityRestController {

    private final QueryEngine queryEngine;

    private Entity getEntityOrThrow(Application application, PathSegmentName entityName) {
        return application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    @GetMapping("/{entityName}")
    public CollectionModel<?> listEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam Map<String, String> params
    ) {
        var entity = getEntityOrThrow(application, entityName);

        var results = queryEngine.query(entity, params, PageRequest.ofPage(page));

        EmbeddedWrappers wrappers = new EmbeddedWrappers(false);
        var models = results.getResults().stream()
                .map(res -> wrappers.wrap(toRepresentationModel(application, entity, res), LinkRelation.of("item")))
                .toList();
        return PagedModel.of(models, new PageMetadata(results.getPageSize(), page, results.getTotalItemCount().count()));
    }

    @GetMapping("/{entityName}/{instanceId}")
    public RepresentationModel<?> getEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable String instanceId
    ) {
        var entity = getEntityOrThrow(application, entityName);

        var result = queryEngine.findById(entity, instanceId);

        return result.map(res -> toRepresentationModel(application, entity, res))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    static RepresentationModel<?> toRepresentationModel(Application application, Entity entity, EntityInstance inst) {
        return RepresentationModel.of(inst)
                .add(linkTo(methodOn(EntityRestController.class)
                        .getEntity(application, entity.getPathSegment(), inst.getId())
                ).withSelfRel());
    }

    @PostMapping("/{entityName}")
    public ResponseEntity<?> createEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @RequestBody Map<String, Object> data
    ) {
        var entity = getEntityOrThrow(application, entityName);

        EntityInstance instance = queryEngine.createInstance(entity, data);
        RepresentationModel<?> model = toRepresentationModel(application, entity, instance);

        return ResponseEntity
                .created(linkTo(methodOn(EntityRestController.class)
                        .getEntity(application, entity.getPathSegment(), instance.getId())).toUri())
                .body(model);
    }
}
