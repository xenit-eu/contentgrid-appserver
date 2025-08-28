package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.PaginationControls;
import java.util.Map;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EncodedCursorSupport {
    public static PaginationControls makeControls(
            @NonNull CursorCodec codec,
            @NonNull EncodedCursorPagination pagination,
            @NonNull EntityName entity,
            @NonNull SortData sort,
            @NonNull Map<String, String> params,
            boolean hasNext
    ) {
        var currentPage = (PageBasedPagination)
                codec.decodeCursor(pagination.getCursorContext(), entity, params);
        var nextPage = new PageBasedPagination(currentPage.getSize(), currentPage.getPage() + 1);
        var next = codec.encodeCursor(nextPage, entity, sort, params).cursor();
        var prevPage = new PageBasedPagination(currentPage.getSize(), currentPage.getPage() - 1);
        var prev = codec.encodeCursor(prevPage, entity, sort, params).cursor();
        var first = codec.encodeCursor(new PageBasedPagination(currentPage.getSize(), 0), entity, sort, params).cursor();
        return new EncodedCursorPaginationControls(
                pagination,
                hasNext ? new EncodedCursorPagination(next, currentPage.getSize(), sort) : null,
                currentPage.isFirstPage() ? null : new EncodedCursorPagination(prev, currentPage.getSize(), sort),
                new EncodedCursorPagination(first, currentPage.getSize(), sort)
        );
    }
}
