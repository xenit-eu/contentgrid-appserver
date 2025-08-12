package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.NonNull;

public interface ContentApi {
    Optional<Content> find(
            @NonNull Application application,
            @NonNull EntityName entityName,
            @NonNull EntityId id,
            @NonNull AttributeName attributeName
    ) throws EntityNotFoundException;

    void update(
            @NonNull Application application,
            @NonNull EntityName entityName,
            @NonNull EntityId id,
            @NonNull AttributeName attributeName,
            @NonNull FileDataEntry file
    ) throws InvalidPropertyDataException;

    void delete(
            @NonNull Application application,
            @NonNull EntityName entityName,
            @NonNull EntityId id,
            @NonNull AttributeName attributeName
    ) throws InvalidPropertyDataException;

    /**
     * Representation of a content object.
     * <p>
     * The stored content is not retrieved until {@link #getInputStream()} is called; all other information is retrieved from metadata
     */
    interface Content {

        /**
         * Create a new content object that only covers the byte-range between {@code start} and {@code endInclusive}
         * @param start The position of the first byte of the range
         * @param endInclusive The position of the last byte of the range (inclusive)
         * @return A new content object whose {@link #getInputStream()} is limited to the given range
         */
        Content withByteRange(long start, long endInclusive);

        /**
         * @return Description
         */
        String getDescription();

        String getFilename();

        long getLength();

        String getMimeType();

        InputStream getInputStream() throws IOException;

    }

}
