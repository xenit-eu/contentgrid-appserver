package com.contentgrid.appserver.domain.paging;

import com.contentgrid.appserver.domain.values.ItemCount;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.hateoas.pagination.api.Pagination;
import com.contentgrid.hateoas.pagination.api.PaginationControls;
import com.contentgrid.hateoas.pagination.api.Slice;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.Value;

@Value
public class ResultSlice implements Slice<EntityData> {
    List<EntityData> entities;

    PaginationControls controls;

    @NonNull
    ItemCount totalItemCount;

    @Override
    public List<EntityData> getContent() {
        return entities;
    }

    @Override
    public Pagination getPagination() {
        return controls.current();
    }

    @Override
    public Pagination current() {
        return controls.current();
    }

    @Override
    public Optional<Pagination> next() {
        return controls.next();
    }

    @Override
    public Optional<Pagination> previous() {
        return controls.previous();
    }

    @Override
    public Pagination first() {
        return controls.first();
    }
}
