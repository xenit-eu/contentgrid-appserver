package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.exception.InvalidSortParameterException;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModel;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.data.ConversionServiceRequestInputData;
import com.contentgrid.appserver.rest.data.MultipartRequestInputData;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToRelationDataEntryConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ETag;
import org.springframework.hateoas.server.core.EmbeddedWrappers;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class EntityRestController {

    public static final String SORT_NAME = "_sort";

    private final DatamodelApi datamodelApi;
    private final ConversionService conversionService;
    private final EntityDataRepresentationModelAssembler assembler;

    private Entity getEntityOrThrow(Application application, PathSegmentName entityName) {
        return application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found"));
    }

    // Workaround for https://github.com/spring-projects/spring-framework/issues/23820
    // We need this so you can have a single ?sort=foo,asc parameter without it splitting on the comma
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String[].class, new StringArrayPropertyEditor(null));
    }

    @GetMapping("/{entityName}")
    public CollectionModel<?> listEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false, name = SORT_NAME) String[] sort,
            @RequestParam Map<String, String> params
    ) {
        var entity = getEntityOrThrow(application, entityName);
        var sortData = parseSortData(sort);

        var results = datamodelApi.findAll(application, entity, params, sortData, null);

        EmbeddedWrappers wrappers = new EmbeddedWrappers(false);
        var models = results.getContent().stream()
                .map(res -> wrappers.wrap(assembler.withContext(application).toModel(res), IanaLinkRelations.ITEM))
                .toList();
        // TODO use page data and count data (ACC-2200)
        return CollectionModel.of(models);
    }

    @GetMapping("/{entityName}/{instanceId}")
    public ResponseEntity<EntityDataRepresentationModel> getEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId
    ) {
        var entity = getEntityOrThrow(application, entityName);

        var result = datamodelApi.findById(
                        application,
                        // For GET, version constraints are not taken into account
                        // because all they would do is omit the body after we have
                        // already queried the database.
                        // All expensive operations have already happened (the body is not that large),
                        // so there is no point in still discarding it
                        EntityRequest.forEntity(entity.getName(), instanceId)
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return ResponseEntity.ok()
                .eTag(calculateETag(result))
                .body(assembler.withContext(application).toModel(result));
    }

    private String calculateETag(EntityData result) {
        return Optional.ofNullable(conversionService.convert(result.getIdentity().getVersion(), ETag.class))
                .map(ETag::formattedTag)
                .orElse(null);
    }

    @PostMapping("/{entityName}")
    public ResponseEntity<EntityDataRepresentationModel> createEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @RequestBody RequestInputData data
    ) throws InvalidPropertyDataException {
        var entity = getEntityOrThrow(application, entityName);

        GenericConversionService conversionService = new GenericConversionService();
        conversionService.addConverter(new StringDataEntryToRelationDataEntryConverter(application));

        var result = datamodelApi.create(
                application,
                entity.getName(),
                new ConversionServiceRequestInputData(data, conversionService)
        );

        var model = assembler.withContext(application).toModel(result);
        return ResponseEntity
                .created(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .eTag(calculateETag(result))
                .body(model);
    }

    @PostMapping(value = "/{entityName}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<EntityDataRepresentationModel> createEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            HttpServletRequest request
    ) throws InvalidPropertyDataException {
        return createEntity(application, entityName, new ConversionServiceRequestInputData(MultipartRequestInputData.fromRequest(request), conversionService));
    }

    @PutMapping("/{entityName}/{id}")
    public ResponseEntity<EntityDataRepresentationModel> update(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            VersionConstraint requestedVersion,
            @RequestBody RequestInputData data
    ) throws InvalidPropertyDataException {
        var entity = getEntityOrThrow(application, entityName);

        try {
            var updateResult = datamodelApi.update(
                    application,
                    EntityRequest.forEntity(entity.getName(), id)
                            .withVersionConstraint(requestedVersion),
                    data);
            return ResponseEntity.ok()
                    .eTag(calculateETag(updateResult))
                    .body(assembler.withContext(application).toModel(updateResult));
        } catch(EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, null, e);
        }
    }

    @PatchMapping("/{entityName}/{id}")
    public ResponseEntity<EntityDataRepresentationModel> updatePartial(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            VersionConstraint requestedVersion,
            @RequestBody RequestInputData data
    ) throws InvalidPropertyDataException {
        var entity = getEntityOrThrow(application, entityName);

        try {
            var updateResult = datamodelApi.updatePartial(
                    application,
                    EntityRequest.forEntity(entity.getName(), id)
                            .withVersionConstraint(requestedVersion),
                    data
            );

            return ResponseEntity.ok()
                    .eTag(calculateETag(updateResult))
                    .body(assembler.withContext(application).toModel(updateResult));
        } catch(EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, null, e);
        }
    }

    private SortData parseSortData(String[] sort) {
        ArrayList<FieldSort> names = new ArrayList<>();

        if (sort == null) {
            return new SortData(names);
        }

        for (String s : sort) {
            var split = s.split(",", 2);
            if (split.length == 2) {
                try {
                    var direction = Direction.valueOf(split[1].toUpperCase());
                    names.add(new FieldSort(direction, SortableName.of(split[0])));
                } catch (IllegalArgumentException e) {
                    throw InvalidSortParameterException.invalidDirection(split[1]);
                }
            } else {
                names.add(new FieldSort(Direction.ASC, SortableName.of(split[0])));
            }
        }

        return new SortData(names);
    }

}
