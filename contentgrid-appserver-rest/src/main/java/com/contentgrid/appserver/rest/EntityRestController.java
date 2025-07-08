package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.PageData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SliceData.PageInfo;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.PagedModel.PageMetadata;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.EmbeddedWrappers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
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
    private final EntityDataRepresentationModelAssembler assembler = new EntityDataRepresentationModelAssembler();

    private Entity getEntityOrThrow(Application application, PathSegmentName entityName) {
        return application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    // Workaround for https://github.com/spring-projects/spring-framework/issues/23820
    // We need this so you have have a single ?sort=foo,asc parameter without it splitting on the comma
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String[].class, new StringArrayPropertyEditor(null));
    }

    @GetMapping("/{entityName}")
    public CollectionModel<?> listEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String[] sort,
            @RequestParam Map<String, String> params
    ) {
        var entity = getEntityOrThrow(application, entityName);
        var sortData = parseSortData(sort);

        var results = datamodelApi.findAll(application, entity, params, sortData, defaultPageData());

        EmbeddedWrappers wrappers = new EmbeddedWrappers(false);
        var models = results.getEntities().stream()
                .map(res -> wrappers.wrap(assembler.withContext(application).toModel(res), IanaLinkRelations.ITEM))
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

        return result.map(res -> assembler.withContext(application).toModel(res))
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

        RepresentationModel<?> model = assembler.withContext(application).toModel(result);
        return ResponseEntity
                .created(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
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

        try {
            datamodelApi.update(application, id, entityData);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var result = datamodelApi.findById(application, entity, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        RepresentationModel<?> model = assembler.withContext(application).toModel(result);
        return ResponseEntity
                .ok()
                .location(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
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

    private SortData parseSortData(String[] sort) {
        ArrayList<FieldSort> names = new ArrayList<>();

        if (sort == null) {
            return new SortData(names);
        }

        for (String s : sort) {
            var split = s.split(",", 2);
            if (split.length == 2) {
                var direction = Direction.valueOf(split[1].toUpperCase());
                names.add(new FieldSort(direction, SortableName.of(split[0])));
            } else {
                names.add(new FieldSort(Direction.ASC, SortableName.of(split[0])));
            }
        }

        return new SortData(names);
    }

}
