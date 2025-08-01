package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.query.engine.api.data.OffsetData;
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
            OffsetData offsetData,
            int size
    ) {
        if (pagination == null) {
            int pageSize = EncodedCursorPagination.PAGE_SIZE;
            var current = codec.encodeCursor(new PageBasedPagination(pageSize, 0), entity.getValue(), sort, params).cursor();
            var next = (size > offsetData.getLimit())
                    ? codec.encodeCursor(new PageBasedPagination(pageSize, 1), entity.getValue(), sort, params).cursor()
                    : null;
            return new EncodedCursorPaginationControls(
                    new EncodedCursorPagination(current, sort),
                    next != null ? new EncodedCursorPagination(next, sort) : null,
                    null
            );
        } else {
            var currentPage = (PageBasedPagination)
                    codec.decodeCursor(pagination.getCursorContext(), entity.getValue(), params);
            var nextPage = new PageBasedPagination(currentPage.getSize(), currentPage.getPage() + 1);
            var next = (size > offsetData.getLimit())
                    ? codec.encodeCursor(nextPage, entity.getValue(), sort, params).cursor()
                    : null;
            var prevPage = new PageBasedPagination(currentPage.getSize(), currentPage.getPage() - 1);
            var prev = !currentPage.isFirstPage()
                    ? codec.encodeCursor(prevPage, entity.getValue(), sort, params).cursor()
                    : null;
            return new EncodedCursorPaginationControls(
                    pagination,
                    next != null ? new EncodedCursorPagination(next, sort) : null,
                    prev != null ? new EncodedCursorPagination(prev, sort) : null
            );
        }
    }
}
