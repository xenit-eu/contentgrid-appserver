package com.contentgrid.appserver.rest.profile;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.rest.profile.assembler.ProfileRootRepresentationModel;
import com.contentgrid.appserver.rest.profile.assembler.ProfileRootRepresentationModelAssembler;
import com.contentgrid.appserver.rest.profile.assembler.hal.ProfileEntityRepresentationModel;
import com.contentgrid.appserver.rest.profile.assembler.hal.ProfileEntityRepresentationModelAssembler;
import com.contentgrid.appserver.rest.profile.assembler.json.JsonSchema;
import com.contentgrid.appserver.rest.profile.assembler.json.JsonSchemaAssembler;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
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
    public ProfileRootRepresentationModel getProfile(Application application, LinkFactoryProvider linkFactoryProvider) {
        return profileRootAssembler.withContext(linkFactoryProvider).toModel(application);
    }

    @SpecializedOnEntity(entityPathVariable = "entityName")
    @GetMapping(value = "/{entityName}", produces = {
            MediaTypes.HAL_FORMS_JSON_VALUE, MediaTypes.HAL_JSON_VALUE
    })
    public ProfileEntityRepresentationModel getHalFormsEntityProfile(
            Application application, @PathVariable PathSegmentName entityName,
            UserLocales userLocales, LinkFactoryProvider linkFactoryProvider
    ) {
        var entity = application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return profileEntityAssembler.withContext(new ProfileEntityRepresentationModelAssembler.Context(application, userLocales, linkFactoryProvider)).toModel(entity);
    }

    @SpecializedOnEntity(entityPathVariable = "entityName")
    @GetMapping(value = "/{entityName}", produces = "application/schema+json")
    public JsonSchema getJsonSchemaEntityProfile(
            Application application, @PathVariable PathSegmentName entityName,
            UserLocales userLocales
    ) {
        var entity = application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return jsonSchemaAssembler.toModel(entity, new JsonSchemaAssembler.Context(application, userLocales));
    }
}
