package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.PaginationControls;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EncodedCursorSupport {
    public static PaginationControls makeControls(
            CursorCodec codec,
            EncodedCursorPagination pagination,
            EntityName entity,
            SortData sort,
            Map<String, String> params,
            boolean hasNext
    ) {
        if (pagination == null) {
            int pageSize = EncodedCursorPagination.PAGE_SIZE;
            var current = codec.encodeCursor(new PageBasedPagination(pageSize, 0), entity.getValue(), sort, params).cursor();
            var currentPagination = new EncodedCursorPagination(current, pageSize, sort);
            var next = hasNext
                    ? codec.encodeCursor(new PageBasedPagination(pageSize, 1), entity.getValue(), sort, params).cursor()
                    : null;
            return new EncodedCursorPaginationControls(
                    currentPagination,
                    next != null ? new EncodedCursorPagination(next, pageSize, sort) : null,
                    null,
                    currentPagination
            );
        } else {
            var currentPage = (PageBasedPagination)
                    codec.decodeCursor(pagination.getCursorContext(), entity.getValue(), params);
            var nextPage = new PageBasedPagination(currentPage.getSize(), currentPage.getPage() + 1);
            var next = hasNext
                    ? codec.encodeCursor(nextPage, entity.getValue(), sort, params).cursor()
                    : null;
            var prevPage = new PageBasedPagination(currentPage.getSize(), currentPage.getPage() - 1);
            var prev = !currentPage.isFirstPage()
                    ? codec.encodeCursor(prevPage, entity.getValue(), sort, params).cursor()
                    : null;
            var first = codec.encodeCursor(new PageBasedPagination(currentPage.getSize(), 0), entity.getValue(), sort, params).cursor();
            return new EncodedCursorPaginationControls(
                    pagination,
                    next != null ? new EncodedCursorPagination(next, currentPage.getSize(), sort) : null,
                    prev != null ? new EncodedCursorPagination(prev, currentPage.getSize(), sort) : null,
                    new EncodedCursorPagination(first, currentPage.getSize(), sort)
            );
        }
    }
}
