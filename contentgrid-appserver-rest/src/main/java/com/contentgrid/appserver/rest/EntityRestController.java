package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.EntityDefinitionNotFoundException;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModel;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.data.ConversionServiceRequestInputData;
import com.contentgrid.appserver.rest.data.MultipartRequestInputData;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToRelationDataEntryConverter;
import com.contentgrid.appserver.rest.exception.RelationTargetNotFoundException;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.mapping.SpecializedOnEntity;
import java.util.HashMap;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ETag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;

@RestController
@SpecializedOnEntity(entityPathVariable = "entityName")
@RequiredArgsConstructor
public class EntityRestController {

    private final DatamodelApi datamodelApi;
    private final ConversionService conversionService;
    private final EntityDataRepresentationModelAssembler assembler;

    private Entity getEntityOrThrow(Application application, PathSegmentName entityName) {
        return application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new EntityDefinitionNotFoundException(entityName.getValue()));
    }

    // Workaround for https://github.com/spring-projects/spring-framework/issues/23820
    // We need this so you can have a single ?sort=foo,asc parameter without it splitting on the comma
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String[].class, new StringArrayPropertyEditor(null));
    }

    @GetMapping("/{entityName}")
    public CollectionModel<EntityDataRepresentationModel> listEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            AuthorizationContext authorizationContext,
            @RequestParam MultiValueMap<String, String> params,
            EncodedCursorPagination pagination,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider
    ) {
        // Remove pagination query parameters
        var paramsWithoutPaging = MultiValueMap.fromMultiValue(new HashMap<>(params));
        paramsWithoutPaging.remove(EncodedCursorPaginationHandlerMethodArgumentResolver.CURSOR_NAME);
        paramsWithoutPaging.remove(EncodedCursorPaginationHandlerMethodArgumentResolver.SIZE_NAME);
        paramsWithoutPaging.remove(EncodedCursorPaginationHandlerMethodArgumentResolver.SORT_NAME);

        var entity = getEntityOrThrow(application, entityName);
        var results = datamodelApi.findAll(application, entity, paramsWithoutPaging, pagination,
                authorizationContext);

        return assembler.withContext(application, entity.getName(), userLocales, linkFactoryProvider, paramsWithoutPaging, pagination)
                .toCollectionModel(results);
    }

    @GetMapping("/{entityName}/{id}")
    public ResponseEntity<EntityDataRepresentationModel> getEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            VersionConstraint versionConstraint,
            WebRequest webRequest,
            AuthorizationContext authorizationContext,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider
    ) {
        var entity = getEntityOrThrow(application, entityName);

        var result = datamodelApi.findById(
                        application,
                        EntityRequest.forEntity(entity.getName(), id)
                                .withVersionConstraint(versionConstraint),
                        authorizationContext
                )
                .orElseThrow(() -> new EntityIdNotFoundException(entity.getName(), id));

        var eTag = calculateETag(result);

        if (webRequest.checkNotModified(eTag)) {
            return null;
        }

        return ResponseEntity.ok()
                .eTag(eTag)
                .body(assembler.withContext(application, entity.getName(), userLocales, linkFactoryProvider).toModel(result));
    }

    private String calculateETag(EntityInstance result) {
        return Optional.ofNullable(conversionService.convert(result.getIdentity().getVersion(), ETag.class))
                .map(ETag::formattedTag)
                .orElse(null);
    }

    @PostMapping("/{entityName}")
    public ResponseEntity<EntityDataRepresentationModel> createEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @RequestBody RequestInputData data,
            AuthorizationContext authorizationContext,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider
    ) throws InvalidPropertyDataException, RelationTargetNotFoundException {
        var entity = getEntityOrThrow(application, entityName);

        GenericConversionService conversionService = new GenericConversionService();
        conversionService.addConverter(new StringDataEntryToRelationDataEntryConverter(application));

        EntityInstance result;
        try {
            result = datamodelApi.create(
                    application,
                    entity.getName(),
                    new ConversionServiceRequestInputData(data, conversionService),
                    authorizationContext
            );
        } catch (EntityIdNotFoundException e) {
            throw new RelationTargetNotFoundException(e);
        }

        var model = assembler.withContext(application, entity.getName(), userLocales, linkFactoryProvider).toModel(result);
        return ResponseEntity
                .created(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .eTag(calculateETag(result))
                .body(model);
    }

    @PostMapping(value = "/{entityName}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<EntityDataRepresentationModel> createEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            NativeWebRequest request,
            AuthorizationContext authorizationContext,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider
    ) throws InvalidPropertyDataException, RelationTargetNotFoundException {
        var inputData = new ConversionServiceRequestInputData(
                MultipartRequestInputData.fromRequest(request),
                conversionService
        );
        return createEntity(application, entityName, inputData, authorizationContext, userLocales, linkFactoryProvider);
    }

    @PutMapping("/{entityName}/{id}")
    public ResponseEntity<?> update(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            VersionConstraint requestedVersion,
            @RequestBody RequestInputData data,
            AuthorizationContext authorizationContext,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider
    ) throws InvalidPropertyDataException {
        var entity = getEntityOrThrow(application, entityName);

        var updateResult = datamodelApi.update(
                application,
                EntityRequest.forEntity(entity.getName(), id)
                        .withVersionConstraint(requestedVersion),
                data,
                authorizationContext
        );
        return ResponseEntity.noContent()
                .eTag(calculateETag(updateResult))
                .build();
    }

    @PatchMapping("/{entityName}/{id}")
    public ResponseEntity<?> updatePartial(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            VersionConstraint requestedVersion,
            @RequestBody RequestInputData data,
            AuthorizationContext authorizationContext,
            UserLocales userLocales,
            LinkFactoryProvider linkFactoryProvider
    ) throws InvalidPropertyDataException {
        var entity = getEntityOrThrow(application, entityName);

        var updateResult = datamodelApi.updatePartial(
                application,
                EntityRequest.forEntity(entity.getName(), id)
                        .withVersionConstraint(requestedVersion),
                data,
                authorizationContext
        );

        return ResponseEntity.noContent()
                .eTag(calculateETag(updateResult))
                .build();
    }

    @DeleteMapping("/{entityName}/{id}")
    public ResponseEntity<?> deleteEntity(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId id,
            VersionConstraint requestedVersion,
            AuthorizationContext authorizationContext
    ) {
        var entity = getEntityOrThrow(application, entityName);

        var request = EntityRequest.forEntity(entity.getName(), id).withVersionConstraint(requestedVersion);
        datamodelApi.deleteEntity(application, request, authorizationContext);
        return ResponseEntity.noContent().build();
    }

}
