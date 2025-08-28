package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
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
            @NonNull Map<String, String> params,
            boolean hasNext
    ) {
        var currentPage = (PageBasedPagination)
                codec.decodeCursor(pagination.getCursorContext(), entity, params);
        var nextPage = new PageBasedPagination(currentPage.getSize(), currentPage.getPage() + 1);
        var next = codec.encodeCursor(nextPage, entity, pagination.getSort(), params).cursor();
        var prevPage = new PageBasedPagination(currentPage.getSize(), currentPage.getPage() - 1);
        var prev = codec.encodeCursor(prevPage, entity, pagination.getSort(), params).cursor();
        var first = codec.encodeCursor(new PageBasedPagination(currentPage.getSize(), 0), entity, pagination.getSort(), params).cursor();
        return new EncodedCursorPaginationControls(
                pagination,
                hasNext ? new EncodedCursorPagination(next, currentPage.getSize(), pagination.getSort()) : null,
                currentPage.isFirstPage() ? null : new EncodedCursorPagination(prev, currentPage.getSize(), pagination.getSort()),
                new EncodedCursorPagination(first, currentPage.getSize(), pagination.getSort())
        );
    }
}
