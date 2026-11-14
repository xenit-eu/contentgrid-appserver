package com.contentgrid.appserver.rest.entity;


import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.mapping.SpecializedOnLinkType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

// TODO ACC-3004 REST. Currently stubbed for href creation
@RestController
@RequiredArgsConstructor
@SpecializedOnLinkType(entityPathVariable = "entityName", linkPathVariable = "*linkPath")
public class DatamodelExtensionLinkRestController {

    @GetMapping("/{entityName}/{entityId}/{*linkPath}")
    public ResponseEntity<Object> getLink(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId entityId,
            @PathVariable PathSegmentName linkPath,
            VersionConstraint versionConstraint,
            WebRequest webRequest,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
            ) {
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .build();
    }

}
