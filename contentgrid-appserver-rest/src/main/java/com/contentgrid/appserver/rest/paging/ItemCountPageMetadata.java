package com.contentgrid.appserver.rest.paging;

import com.contentgrid.appserver.domain.paging.ItemCount;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import org.springframework.hateoas.PagedModel.PageMetadata;

public class ItemCountPageMetadata extends PageMetadata {

    @NonNull
    private final ItemCount totalItemCount;

    @NonNull
    private final CursorPageMetadata cursorPageMetadata;

    public ItemCountPageMetadata(
            PageMetadata metadata,
            ItemCount totalItemCount,
            CursorPageMetadata cursorPageMetadata
    ) {
        super(metadata.getSize(), metadata.getNumber(), metadata.getTotalElements(), metadata.getTotalPages());
        this.totalItemCount = totalItemCount;
        this.cursorPageMetadata = cursorPageMetadata;
    }

    @JsonProperty("total_items_estimate")
    public long getEstimatedTotalItems() {
        return totalItemCount.count();
    }

    @JsonProperty("total_items_exact")
    @JsonInclude(Include.NON_NULL)
    public Long getExactTotalItems() {
        if (!totalItemCount.isEstimated()) {
            return totalItemCount.count();
        }

        return null;
    }

    public String getCursor() {
        return cursorPageMetadata.getCursor();
    }

    @JsonProperty("prev_cursor")
    @JsonInclude(Include.NON_NULL)
    public String getPreviousCursor() {
        return cursorPageMetadata.getPreviousCursor();
    }

    @JsonProperty("next_cursor")
    @JsonInclude(Include.NON_NULL)
    public String getNextCursor() {
        return cursorPageMetadata.getNextCursor();
    }

}
