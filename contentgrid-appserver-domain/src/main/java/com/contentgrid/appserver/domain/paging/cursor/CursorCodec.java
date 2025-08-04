package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.Pagination;
import java.util.Map;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.StandardException;

/**
 * Encoder and decoder for cursors
 */
public interface CursorCodec {

    /**
     * Decodes a cursor to a contentgrid-hateoas pagination
     *
     * @param context The cursor to decode
     * @param entityName The entity path segment of the URI
     * @param params The query parameters of the URI, without cursor, page size or sort parameters
     * @return Contentgrid-hateoas pagination, decoded from the cursor
     * @throws CursorDecodeException When a cursor can not be decoded
     */
    Pagination decodeCursor(CursorContext context, String entityName, Map<String, String> params) throws CursorDecodeException;

    /**
     * Encodes a contentgrid hateoas pagination to a cursor
     *
     * @param pagination The contentgrid hateoas pagination
     * @param entityName The entity path segment of the URI
     * @param params The query parameters of the URI, without cursor, page size or sort parameters
     * @return The cursor that can be used in a request
     */
    CursorContext encodeCursor(Pagination pagination, String entityName, SortData sort, Map<String, String> params);

    /**
     * The cursor with its context.
     * <p>
     * This object represents the pagination information as encoded in a request
     *
     * @param cursor The cursor (can be null if no cursor is present in the request)
     * @param pageSize The size of a page
     * @param sort Sorting of the resultset
     */
    @Builder
    record CursorContext(String cursor, int pageSize, @NonNull SortData sort) {

        public CursorContext mapCursor(@NonNull UnaryOperator<@NonNull String> cursorMapper) {
            if (cursor == null) {
                return this;
            }
            return new CursorContext(cursorMapper.apply(cursor), pageSize, sort);
        }

    }

    /**
     * Thrown when a cursor can not be decoded for any reason
     */
    @StandardException
    class CursorDecodeException extends RuntimeException {

    }
}
