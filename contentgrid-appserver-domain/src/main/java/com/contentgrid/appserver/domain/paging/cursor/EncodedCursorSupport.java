package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.domain.paging.PageBasedPaginationControls;
import com.contentgrid.hateoas.pagination.api.PaginationControls;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EncodedCursorSupport {
    public static PaginationControls makeControls(
            @NonNull CursorCodec codec,
            @NonNull EncodedCursorPagination pagination,
            @NonNull EntityName entity,
            @NonNull Map<String, List<String>> params,
            boolean hasNext
    ) {
        var currentPage = (PageBasedPagination)
                codec.decodeCursor(pagination.getCursorContext(), entity, params);
        var paginationControls = PageBasedPaginationControls.forPagination(currentPage, hasNext);
        return new EncodedCursorPaginationControls(paginationControls, page -> {
            var context = codec.encodeCursor(page, entity, pagination.getSort(), params);
            return new EncodedCursorPagination(context.cursor(), context.pageSize(), context.sort());
        });
    }
}
