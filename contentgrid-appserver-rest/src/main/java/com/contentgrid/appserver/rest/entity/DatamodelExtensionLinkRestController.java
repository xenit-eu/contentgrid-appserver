package com.contentgrid.appserver.rest.entity;


import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
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
public class DatamodelExtensionLinkRestController {

    @GetMapping("/{entityName}/{entityId}/_links/{relKey}")
    public ResponseEntity<Object> getUnnamedLink(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId entityId,
            @PathVariable PathSegmentName relKey,
            VersionConstraint versionConstraint,
            WebRequest webRequest,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .build();
    }

    @GetMapping("/{entityName}/{entityId}/_links/{relKey}/{linkName}")
    public ResponseEntity<Object> getNamedLink(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId entityId,
            @PathVariable PathSegmentName relKey,
            @PathVariable PathSegmentName linkName,
            VersionConstraint versionConstraint,
            WebRequest webRequest,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
            ) {
        return ResponseEntity
                .status(HttpStatus.NOT_IMPLEMENTED)
                .build();
    }

    @GetMapping("/{entityName}/{entityId}/{propertyName}/_links/{relKey}")
    public ResponseEntity<Object> getOwnedLink(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId entityId,
            @PathVariable PathSegmentName propertyName,
            @PathVariable PathSegmentName relKey,
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
