package com.contentgrid.appserver.rest.property;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.domain.ContentApi;
import com.contentgrid.appserver.domain.ContentApi.Content;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.exception.EntityNotFoundException;
import com.contentgrid.appserver.rest.exception.UnsatisfiableRangeHttpException;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType.PropertyType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@SpecializedOnPropertyType(type = PropertyType.CONTENT_ATTRIBUTE, entityPathVariable = "entityName", propertyPathVariable = "propertyName")
@RequestMapping("/{entityName}/{instanceId}/{propertyName}")
public class ContentRestController {

    private final ContentApi contentApi;

    private EntityAndContentAttribute resolve(Application application, PathSegmentName entityName, PathSegmentName propertyName) {
        var entity = application.getEntityByPathSegment(entityName).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var contentProperty = entity.getContentByPathSegment(propertyName).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new EntityAndContentAttribute(entity.getName(), contentProperty);
    }

    record EntityAndContentAttribute(
            @NonNull EntityName entityName,
            @NonNull ContentAttribute attribute
    ) {
        public AttributeName attributeName() {
            return attribute.getName();
        }
    }

    @GetMapping
    public ResponseEntity<Resource> getContent(
            @RequestHeader HttpHeaders httpHeaders,
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) {
        var entityAndContent = resolve(application, entityName, propertyName);

        var content = contentApi.find(
                application,
                entityAndContent.entityName(),
                instanceId,
                entityAndContent.attributeName()
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var resource = toResource(content, parseRanges(httpHeaders));

        // Content-Type
        var contentType = MediaType.parseMediaType(content.getMimeType());

        // Content-Disposition
        var contentDispositionBuilder = ContentDisposition.attachment();
        if(content.getFilename() != null) {
            contentDispositionBuilder.filename(content.getFilename(), StandardCharsets.UTF_8);
        }
        var contentDisposition = contentDispositionBuilder.build();

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    private static List<HttpRange> parseRanges(HttpHeaders httpHeaders) {
        if(!httpHeaders.containsKey(HttpHeaders.RANGE)) {
            return List.of();
        }
        try {
            var ranges = httpHeaders.getRange();
            if(ranges.isEmpty()) {
                // As per https://datatracker.ietf.org/doc/html/rfc9110#section-14.1.1; at least one range specifier is required
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one range specifier is required");
            }
            return ranges;
        } catch(IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can not parse Range header", e);
        }
    }

    private static Resource toResource(Content content, List<HttpRange> ranges) {
        if(ranges.isEmpty()) {
            return new ContentResource(content);
        }

        var start = ranges.stream()
                .mapToLong(r -> r.getRangeStart(content.getLength()))
                .min()
                .orElseThrow();
        var end = ranges.stream()
                .mapToLong(r -> r.getRangeEnd(content.getLength()))
                .max()
                .orElseThrow();

        if(start > content.getLength()) {
            throw new UnsatisfiableRangeHttpException(content.getLength());
        }

        return new ContentResource(content.withByteRange(start, end));
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
    @RequestMapping(method = {RequestMethod.HEAD})
    public ResponseEntity<Resource> headContent(
            @RequestHeader HttpHeaders httpHeaders,
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) {
        var response = getContent(httpHeaders, application, entityName, instanceId, propertyName);

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(new EmptyContentResourceProxy(response.getBody()));
    }

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT}, consumes = "*/*")
    public ResponseEntity<?> setContent(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) MediaType contentType,
            @RequestBody InputStreamResource requestBody
    ) throws InvalidPropertyDataException {
        var entityAndContent = resolve(application, entityName, propertyName);

        var fileData = new FileDataEntry(
                requestBody.getFilename(),
                contentType.toString(),
                requestBody::getInputStream
        );

        try {
            contentApi.update(
                    application,
                    entityAndContent.entityName(),
                    instanceId,
                    entityAndContent.attributeName(),
                    fileData
            );
        } catch(EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, null, e);
        }

        return ResponseEntity.noContent().build();
    }

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT}, consumes = "multipart/form-data")
    public ResponseEntity<?> setContent(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName,
            @RequestParam MultipartFile file
    ) throws InvalidPropertyDataException {
        var entityAndContent = resolve(application, entityName, propertyName);

        var fileData = new FileDataEntry(
                file.getOriginalFilename(),
                Optional.ofNullable(file.getContentType())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST)),
                file::getInputStream
        );

        try {
            contentApi.update(
                    application,
                    entityAndContent.entityName(),
                    instanceId,
                    entityAndContent.attributeName(),
                    fileData
            );
        } catch(EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, null, e);
        }

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<?> deleteContent(
            Application application,
            @PathVariable PathSegmentName entityName,
            @PathVariable EntityId instanceId,
            @PathVariable PathSegmentName propertyName
    ) throws InvalidPropertyDataException {
        var entityAndContent = resolve(application, entityName, propertyName);
        try {
            contentApi.delete(
                    application,
                    entityAndContent.entityName(),
                    instanceId,
                    entityAndContent.attributeName()
            );
        } catch(EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, null, e);
        }

        return ResponseEntity.noContent().build();
    }


    @EqualsAndHashCode(callSuper = false)
    private static class ContentResource extends AbstractResource {
        @NonNull
        private final Content content;

        public ContentResource(Content content) {
            this.content = content;
        }

        @Override
        public long contentLength() {
            return content.getLength();
        }

        @Override
        public String getDescription() {
            return content.getDescription();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return content.getInputStream();
        }

        @Override
        public boolean exists() {
            return true;
        }
    }

    @RequiredArgsConstructor
    private static class EmptyContentResourceProxy implements Resource {
        @Delegate(types = Resource.class, excludes = InputStreamSource.class)
        @NonNull
        private final Resource delegate;

        @Override
        public InputStream getInputStream() throws IOException {
            return new ZeroInputStream(delegate.contentLength());
        }

        /**
         * InputStream that emulates the same length as the original content object, but filled with NUL-bytes instead of
         * the actual file contents.
         * <p>
         * We can't use {@link InputStream#nullInputStream()} instead, because that is a stream with a length of 0 bytes,
         * which doesn't match the size of the original input stream closely enough to make Content-Length and byte-ranges work properly
         */
        @RequiredArgsConstructor
        private static class ZeroInputStream extends InputStream {
            private final long size;
            private long currentPosition = 0;

            @Override
            public int read() {
                if(currentPosition < size) {
                    currentPosition++;
                    return 0;
                }
                return -1;
            }

            @Override
            public int available() {
                return (int)Math.min(Integer.MAX_VALUE, size - currentPosition);
            }

            @Override
            public int read(byte[] b, int off, int len) {
                Objects.checkFromIndexSize(off, len, b.length);
                if(currentPosition >= size) {
                    return -1;
                }
                var skipped = (int)skip(len);
                for(int i = off; i<skipped;i++)
                    b[i] = 0;
                return skipped;
            }

            @Override
            public long skip(long n) {
                if(n<=0) {
                    return 0;
                }
                var toSkip = Math.min(n, available());
                currentPosition+=toSkip;
                return toSkip;
            }
        }
    }

}
