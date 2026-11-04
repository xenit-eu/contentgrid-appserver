package com.contentgrid.appserver.rest.entity;


import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.exceptions.InvalidEntityLinkException;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.exceptions.EntityDefinitionNotFoundException;
import com.contentgrid.appserver.application.model.links.StoredEntityLink;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.StoredLinkValue.ContentValue;
import com.contentgrid.appserver.domain.StoredLinkValue.TextValue;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry.InputStreamSupplier;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.LinkRequest;
import com.contentgrid.appserver.domain.values.exception.LinkNotFoundException;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.rest.VersionValidator;
import com.contentgrid.appserver.rest.entity.util.ContentResource;
import com.contentgrid.appserver.rest.entity.util.EmptyContentResourceProxy;
import com.contentgrid.appserver.rest.exception.ContentNotFoundException;
import com.contentgrid.appserver.rest.exception.ForbiddenRequestHeaderException;
import com.contentgrid.appserver.rest.hal.links.factory.LinkFactoryProvider;
import com.contentgrid.appserver.rest.mapping.SpecializedOnLinkType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnLinkType.StoredLinkType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

    /*
     * Doing a custom implementation for HEAD here instead of the default that calls the GET method and
     * discards the body.
     *
     * HEAD requests are supposed to be a 'cheap' way to check what would be the answer to a GET request,
     * without retrieving the body.
     * Since no body is being written, and we have all the metadata stored in our database,
     * we can avoid retrieving the content from the ContentStore at all, which saves some processing time and data transfer.
     * We can completely avoid doing some of the work by not making any request to the ContentStore to receive content.
     * Note that we still do need to return a properly-sized InputStream, because Spring does read the body.
     * Especially when servicing Range requests, Spring will want to skip a certain number of bytes, and will throw
     * when that doesn't happen.
     * It's only Tomcat, after the whole Spring stack has finished, that copies the InputStream to a discard OutputStream
     * (instead of the actual HTTP response body as done for a GET).
     */
    @RequestMapping(value ="/{entityName}/{entityId}/{*linkPath}", method = {RequestMethod.HEAD})
    public ResponseEntity<Object> headLink(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId entityId,
            @PathVariable PathSegmentName linkPath,
            VersionConstraint versionConstraint,
            WebRequest webRequest,
            AuthorizationContext authorizationContext,
            LinkFactoryProvider linkFactoryProvider
            ) {
        var response = getLink(application, entityName, entityId, linkPath, versionConstraint, webRequest, authorizationContext, linkFactoryProvider);
        if (response == null) {
            return null;
        }
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(new EmptyContentResourceProxy(response.getBody()));
    }

    @GetMapping("/{entityName}/{entityId}/{*linkPath}")
    public ResponseEntity<Resource> getLink(
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
        ).orElseThrow(() -> new LinkNotFoundException(linkRequest));

        return switch(targetLink) {
            case ContentValue contentValue -> {
                var content = contentValue.getContent();
                if (content.getMimeType() == null) {
                    throw new ContentNotFoundException(EntityIdentity.forEntity(entity.getName(), entityId),
                            storedLink.getStorage().getLast());
                }

                if (versionValidator.checkVersion(webRequest, versionConstraint, content.getVersion())) {
                    yield null; // The response headers inside webRequest are modified as side effect
                }

                var disposition = ContentDisposition.attachment();
                if (content.getFilename() != null) {
                    disposition.filename(content.getFilename(), StandardCharsets.UTF_8);
                }
                yield ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(content.getMimeType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.build().toString())
                        .eTag(versionValidator.calculateETag(content.getVersion()))
                        .body(new ContentResource(content));
            }
            case TextValue textValue -> {
                if (versionValidator.checkVersion(webRequest, versionConstraint, textValue.getVersion())) {
                    yield null;
                }
                yield ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .eTag(versionValidator.calculateETag(textValue.getVersion()))
                        .body(new ByteArrayResource(textValue.getValue().getBytes()));
            }
        };
    }

    @SpecializedOnLinkType(type = StoredLinkType.TEXT, entityPathVariable = "entityName", linkPathVariable = "*linkPath")
    @PutMapping(value = "/{entityName}/{entityId}/{*linkPath}", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Object> updateTextLink(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId entityId,
            @PathVariable PathSegmentName linkPath,
            VersionConstraint versionConstraint,
            @RequestHeader(value = HttpHeaders.CONTENT_RANGE, required = false) String contentRange,
            @RequestBody String requestBody,
            AuthorizationContext authorizationContext
    ) throws ForbiddenRequestHeaderException {
        if (StringUtils.hasText(contentRange)) {
            // Partial PUT is not supported
            throw new ForbiddenRequestHeaderException(HttpHeaders.CONTENT_RANGE);
        }
        var entity = getEntityOrThrow(application, entityName);
        var storedLink = getStoredLinkOrThrow(entity, linkPath);
        var linkRequest = LinkRequest.forLink(
                entity.getName(),
                entityId,
                storedLink.getIdentity(),
                versionConstraint
        );
        // TODO ACC-3319
        var newValue = datamodelApi.updateLink(
                application,
                linkRequest,
                new StringDataEntry(requestBody),
                authorizationContext
        );
        return ResponseEntity.noContent()
                .eTag(versionValidator.calculateETag(newValue.getVersion()))
                .build();
    }

    @SpecializedOnLinkType(type = StoredLinkType.CONTENT, entityPathVariable = "entityName", linkPathVariable = "*linkPath")
    @PutMapping(value = "/{entityName}/{entityId}/{*linkPath}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Object> updateContentLink(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId entityId,
            @PathVariable PathSegmentName linkPath,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) MediaType contentType,
            VersionConstraint versionConstraint,
            @RequestHeader(value = HttpHeaders.CONTENT_DISPOSITION, required = false) String contentDisposition,
            @RequestHeader(value = HttpHeaders.CONTENT_RANGE, required = false) String contentRange,
            @RequestBody(required = false) InputStreamResource requestBody,
            AuthorizationContext authorizationContext
    ) throws ForbiddenRequestHeaderException {
        if (StringUtils.hasText(contentRange)) {
            // Partial PUT is not supported
            throw new ForbiddenRequestHeaderException(HttpHeaders.CONTENT_RANGE);
        }
        var entity = getEntityOrThrow(application, entityName);
        var storedLink = getStoredLinkOrThrow(entity, linkPath);
        var linkRequest = LinkRequest.forLink(
                entity.getName(),
                entityId,
                storedLink.getIdentity(),
                versionConstraint
        );

        String filename = null;
        if (contentDisposition != null) {
            try {
                filename = ContentDisposition.parse(contentDisposition).getFilename();
            } catch (IllegalArgumentException e) {
                // ignore, filename stays null
            }
        }
        InputStreamSupplier inputStreamSupplier = requestBody != null ? requestBody::getInputStream : InputStream::nullInputStream;
        // TODO ACC-3319
        var newValue = datamodelApi.updateLink(
                application,
                linkRequest,
                new FileDataEntry(filename, contentType.toString(), inputStreamSupplier),
                authorizationContext
        );
        return ResponseEntity.noContent()
                .eTag(versionValidator.calculateETag(newValue.getVersion()))
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
        // TODO ACC-3319
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
