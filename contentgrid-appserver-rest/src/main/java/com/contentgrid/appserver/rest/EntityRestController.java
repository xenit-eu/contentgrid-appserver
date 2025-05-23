package com.contentgrid.appserver.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SliceData.PageInfo;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssemblerProvider;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EntityRestController {

    private final DatamodelApi datamodelApi;
    private final EntityDataRepresentationModelAssemblerProvider assemblerProvider;

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

        var results = datamodelApi.findAll(application, entity, params, defaultPageData());

        var assembler = assemblerProvider.getAssemblerFor(application);
        EmbeddedWrappers wrappers = new EmbeddedWrappers(false);
        var models = results.getEntities().stream()
                .map(res -> wrappers.wrap(assembler.toModel(res), LinkRelation.of("item")))
                .toList();
        return PagedModel.of(models, fromPageInfo(results.getPageInfo()));
    }

    @GetMapping("/{entityName}/{instanceId}")
    public RepresentationModel<?> getEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId
    ) {
        var entity = getEntityOrThrow(application, entityName);

        var result = datamodelApi.findById(application, entity, instanceId);

        return result.map(res -> assemblerProvider.getAssemblerFor(application).toModel(res))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{entityName}")
    public ResponseEntity<?> createEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @RequestBody Map<String, Object> data
    ) {
        var entity = getEntityOrThrow(application, entityName);

        var converted = EntityDataValidator.validate(entity, data);
        var entityData = createEntityData(converted, entity);

        var id = datamodelApi.create(application, entityData, List.of());
        var result = datamodelApi.findById(application, entity, id).orElseThrow();

        RepresentationModel<?> model = assemblerProvider.getAssemblerFor(application).toModel(result);
        return ResponseEntity
                .created(linkTo(methodOn(EntityRestController.class)
                        .getEntity(application, entity.getPathSegment(), id)).toUri())
                .body(model);
    }

    @PutMapping("/{entityName}/{id}")
    public ResponseEntity<?> update(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            @RequestBody Map<String, Object> data
    ) {
        var entity = getEntityOrThrow(application, entityName);

        var converted = EntityDataValidator.validate(entity, data);
        var entityData = createEntityData(converted, entity);

        datamodelApi.update(application, id, entityData);
        var result = datamodelApi.findById(application, entity, id).orElseThrow();

        RepresentationModel<?> model = assemblerProvider.getAssemblerFor(application).toModel(result);
        return ResponseEntity
                .ok()
                .location(linkTo(methodOn(EntityRestController.class)
                        .getEntity(application, entity.getPathSegment(), id)).toUri())
                .body(model);
    }

    private static EntityData createEntityData(Map<String, Object> data, Entity entity) {
        // Should this just be rolled into the EntityDataValidator? 🤔
        return EntityData.builder().name(entity.getName()).attributes(data.entrySet().stream()
                .map(entry -> {
                    var providedAttributeName = entry.getKey();
                    var attributeData = entry.getValue();
                    var attribute = entity.getAttributeByName(AttributeName.of(providedAttributeName)).orElseThrow();

                    if (attribute instanceof SimpleAttribute simp) {
                        return SimpleAttributeData.builder().name(simp.getName()).value(attributeData).build();
                    }
                    // POSTing CompositeAttributes is for when we support content
                    return null;

                }).toList()
        ).build();
    }

    // TODO: ACC-2048: support paging
    private static PageData defaultPageData() {
        return new PageData() {
            @Override
            public int getSize() {
                return 20;
            }

            @Override
            public int getPage() {
                return 0;
            }
        };
    }

    // TODO: ACC-2048: support paging
    private static PageMetadata fromPageInfo(PageInfo pageInfo) {
        return new PageMetadata(
                pageInfo.getSize() == null ? 20 : pageInfo.getSize(),
                pageInfo.getStart() == null ? 0 : pageInfo.getStart(),
                pageInfo.getExactCount() == null
                        ? pageInfo.getEstimatedCount() == null ? 0 : pageInfo.getEstimatedCount()
                        : pageInfo.getExactCount()
        );
    }
}
