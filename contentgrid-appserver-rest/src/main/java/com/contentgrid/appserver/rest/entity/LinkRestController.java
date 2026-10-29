package com.contentgrid.appserver.rest.entity;


import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.exceptions.InvalidEntityLinkException;
import com.contentgrid.appserver.application.model.links.LinkIdentity;
import com.contentgrid.appserver.application.model.links.LinkIdentity.NamedLink;
import com.contentgrid.appserver.application.model.links.LinkIdentity.UnnamedLink;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.LinkRequest;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.rest.VersionValidator;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.mapping.SpecializedOnLinkType;
import java.net.URI;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

// TODO ACC-3004 REST. Currently stubbed for href creation
@RestController
@RequiredArgsConstructor
@SpecializedOnLinkType(entityPathVariable = "entityName", linkPathVariable = "*linkPath")
public class LinkRestController {

    private final DatamodelApi datamodelApi;
    private final EntityDataRepresentationModelAssembler assembler;
    private final VersionValidator versionValidator;

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
        var entityNameObj = EntityName.of(entityName.getValue());
        var linkIdentity = mapLinkPathToLinkIdententity(linkPath);
        var linkRequest = LinkRequest.forLink(
                entityNameObj,
                entityId,
                linkIdentity,
                versionConstraint
        ) ;
        var targetLink = datamodelApi.findLink(
                application,
                linkRequest,
                authorizationContext
        );
        return ResponseEntity.ok(targetLink); // TODO ACC-3004 Etag
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
        var entityNameObj = EntityName.of(entityName.getValue());
        var linkIdentity = mapLinkPathToLinkIdententity(linkPath);
        var linkRequest = LinkRequest.forLink(
                entityNameObj,
                entityId,
                linkIdentity,
                versionConstraint
        ) ;
        datamodelApi.deleteLink(
                application,
                linkRequest,
                authorizationContext
        );
        return ResponseEntity.noContent().build();
    }

    // TODO ACC-3004 make less implicit ?
    private LinkIdentity mapLinkPathToLinkIdententity(@NonNull PathSegmentName linkPath) {
        var strippedLinkPath = linkPath.getValue().substring(1);
        var pathElements = List.of(strippedLinkPath.split("/"));
        return  switch (pathElements.size()) {
            case 2 -> new UnnamedLink(URI.create(strippedLinkPath)); // <entity>/{id}/_links/<relkey>
            case 3 -> {
                if ("_links".equals(pathElements.get(0))) {
                    yield new NamedLink(URI.create(pathElements.get(1)), pathElements.get(2)); // <entity>/{id}/_links/<relkey>/<link-name>
                } else {
                    yield new NamedLink(URI.create(pathElements.get(2)), pathElements.get(0)); // <entity>/{id}/<attribute>/_links/<relkey> || <entity>/{id}/<relation>/_links/<relkey>
                }
            }
            default -> { throw new InvalidEntityLinkException(); }
        };
    }
}
