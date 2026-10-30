package com.contentgrid.appserver.rest.entity;


import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.exceptions.InvalidEntityLinkException;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.EntityDefinitionNotFoundException;
import com.contentgrid.appserver.application.model.links.StoredEntityLink;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.StoredLinkValue.ContentValue;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.LinkRequest;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.rest.VersionValidator;
import com.contentgrid.appserver.rest.exception.ContentNotFoundException;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.mapping.SpecializedOnLinkType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequiredArgsConstructor
@SpecializedOnLinkType(entityPathVariable = "entityName", linkPathVariable = "*linkPath")
public class LinkRestController {

    private final DatamodelApi datamodelApi;
    private final VersionValidator versionValidator;

    private Entity getEntityOrThrow(Application application, PathSegmentName entityName) {
        return application.getEntityByPathSegment(entityName)
                .orElseThrow(() -> new EntityDefinitionNotFoundException(entityName.getValue()));
    }

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
        var entity = getEntityOrThrow(application, entityName);
        var storedLink = getStoredLinkOrThrow(entity, linkPath);
        var linkRequest = LinkRequest.forLink(
                entity.getName(),
                entityId,
                storedLink.getIdentity(),
                versionConstraint
        ) ;
        var targetLink = datamodelApi.findLink(
                application,
                linkRequest,
                authorizationContext
        );

        if (targetLink.orElse(null) instanceof ContentValue contentValue) {
            var content = contentValue.getContent();
            if (content.getMimeType() == null) {
                throw new ContentNotFoundException(EntityIdentity.forEntity(entity.getName(), entityId),
                        storedLink.getStorage().getLast());
            }

            if (versionValidator.checkVersion(webRequest, versionConstraint, content.getVersion())) {
                return null; // The response headers inside webRequest are modified as side effect
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(content.getMimeType()))
                    .eTag(versionValidator.calculateETag(content.getVersion()))
                    .body(new InputStreamResource(content::getInputStream));
        }

        return ResponseEntity.ok(targetLink);
    }

    @PutMapping("/{entityName}/{entityId}/{*linkPath}")
    public ResponseEntity<Object> updateLink(
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

    @DeleteMapping("/{entityName}/{entityId}/{*linkPath}")
    public ResponseEntity<Object> deleteLink(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId entityId,
            @PathVariable PathSegmentName linkPath,
            VersionConstraint versionConstraint,
            AuthorizationContext authorizationContext
    ) {
        var entity = getEntityOrThrow(application, entityName);
        var storedLink = getStoredLinkOrThrow(entity, linkPath);
        var linkRequest = LinkRequest.forLink(
                entity.getName(),
                entityId,
                storedLink.getIdentity(),
                versionConstraint
        ) ;
        datamodelApi.deleteLink(
                application,
                linkRequest,
                authorizationContext
        );
        return ResponseEntity.noContent().build();
    }

    private StoredEntityLink getStoredLinkOrThrow(@NonNull Entity entity, @NonNull PathSegmentName linkPath) {
        // Spring includes the leading slash in the value of a multi-segment parameter
        var strippedLinkPath = PathSegmentName.of(linkPath.getValue().substring(1));
        return entity.getStoredEntityLinkByPathSegments(strippedLinkPath)
                .orElseThrow(InvalidEntityLinkException::new);
    }
}
