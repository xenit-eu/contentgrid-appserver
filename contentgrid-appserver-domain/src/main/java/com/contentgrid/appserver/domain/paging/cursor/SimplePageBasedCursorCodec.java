package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.Pagination;
import java.util.Map;

/**
 * Page-based cursor: Re-implementation of the standard numeric page-based strategy
 */
public class SimplePageBasedCursorCodec implements CursorCodec {

    @Override
    public PageBasedPagination decodeCursor(CursorContext context, String entityName, Map<String, String> params) throws CursorDecodeException {
        int pageNumber = 0;
        if (context.cursor() != null && !context.cursor().isBlank()) {
            try {
                pageNumber = Integer.parseInt(context.cursor());
                if (pageNumber < 0) {
                    throw new CursorDecodeException("may not be negative");
                }
            } catch (NumberFormatException ex) {
                throw new CursorDecodeException("must be a number", ex);
            }
        }
        return new PageBasedPagination(context.pageSize(), pageNumber);
    }

    @Override
    public CursorContext encodeCursor(Pagination pagination, String entityName, SortData sort, Map<String, String> params) {
        PageBasedPagination page = (PageBasedPagination) pagination;

        return new CursorContext(Integer.toString(page.getPage()), page.getSize(), sort);
    }

}
