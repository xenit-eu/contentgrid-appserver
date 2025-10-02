package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.rest.assembler.EmptyRepresentationModel;
import com.contentgrid.appserver.rest.assembler.profile.ProfileRootRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.profile.hal.ProfileEntityRepresentationModel;
import com.contentgrid.appserver.rest.assembler.profile.hal.ProfileEntityRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchemaAssembler;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.mapping.SpecializedOnEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileRestController {

    private final ProfileRootRepresentationModelAssembler profileRootAssembler = new ProfileRootRepresentationModelAssembler();
    private final ProfileEntityRepresentationModelAssembler profileEntityAssembler;
    private final JsonSchemaAssembler jsonSchemaAssembler = new JsonSchemaAssembler();

    @GetMapping
    public EmptyRepresentationModel getProfile(Application application, LinkFactoryProvider linkFactoryProvider) {
        return profileRootAssembler.withContext(linkFactoryProvider).toModel(application);
    }

    @SpecializedOnEntity(entityPathVariable = "entityName")
    @GetMapping(value = "/{entityName}", produces = MediaTypes.HAL_FORMS_JSON_VALUE)
    public ProfileEntityRepresentationModel getHalFormsEntityProfile(
            Application application, @PathVariable PathSegmentName entityName,
            LinkFactoryProvider linkFactoryProvider
    ) {
        var entity = application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return profileEntityAssembler.withContext(new ProfileEntityRepresentationModelAssembler.Context(application,
                linkFactoryProvider)).toModel(entity);
    }

    @SpecializedOnEntity(entityPathVariable = "entityName")
    @GetMapping(value = "/{entityName}", produces = "application/schema+json")
    public JsonSchema getJsonSchemaEntityProfile(
            Application application, @PathVariable PathSegmentName entityName
    ) {
        var entity = application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return jsonSchemaAssembler.toModel(application, entity);
    }
}
