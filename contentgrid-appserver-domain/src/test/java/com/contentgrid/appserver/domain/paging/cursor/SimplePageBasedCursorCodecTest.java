package com.contentgrid.appserver.domain.paging.cursor;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorContext;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorDecodeException;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.Direction;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimplePageBasedCursorCodecTest {

    CursorCodec codec = new SimplePageBasedCursorCodec();

    private static final SortData SORT = new SortData(List.of(new FieldSort(Direction.ASC, SortableName.of("abc")), new FieldSort(Direction.DESC, SortableName.of("def"))));
    private static final EntityName ENTITY = EntityName.of("test");
    private static final Map<String, List<String>> PARAMS = Map.of();

    @Test
    void decodeCursorFromNumber() throws CursorDecodeException {
        var decoded = codec.decodeCursor(CursorContext.builder()
                        .cursor("5")
                        .pageSize(15)
                        .sort(SORT)
                        .build(),
                ENTITY,
                PARAMS
        );

        assertThat(decoded.getLimit()).isEqualTo(15);
        assertThat(decoded).isInstanceOfSatisfying(PageBasedPagination.class, pagination -> {
            assertThat(pagination.getPage()).isEqualTo(5);
        });
    }

    @Test
    void decodeCursorFromNegativeNumber() {
        assertThatThrownBy(() -> {
            codec.decodeCursor(CursorContext.builder().cursor("-8").pageSize(15).sort(SORT).build(), ENTITY, PARAMS);
        }).isInstanceOf(CursorDecodeException.class);
    }

    @Test
    void decodeCursorFromNonNumber() {
        assertThatThrownBy(() -> {
            codec.decodeCursor(CursorContext.builder().cursor("blabla").pageSize(15).sort(SORT).build(),
                    ENTITY, PARAMS);
        }).isInstanceOf(CursorDecodeException.class);
    }

    @Test
    void decodeCursorFromNull() throws CursorDecodeException {
        var decoded = codec.decodeCursor(CursorContext.builder().cursor(null).pageSize(15).sort(SORT).build(),
                ENTITY, PARAMS);

        assertThat(decoded.getLimit()).isEqualTo(15);
        assertThat(decoded).isInstanceOfSatisfying(PageBasedPagination.class, pagination -> {
            assertThat(pagination.getPage()).isEqualTo(0);
        });
    }

    @Test
    void encodeCursorFirstPage() {
        var cursor = codec.encodeCursor(new PageBasedPagination(34, 0), ENTITY, SORT, PARAMS);

        assertThat(cursor.cursor()).isEqualTo("0");
        assertThat(cursor.pageSize()).isEqualTo(34);
        assertThat(cursor.sort()).isEqualTo(SORT);
    }

    @Test
    void encodeCursor() {
        var cursor = codec.encodeCursor(new PageBasedPagination(34, 12), ENTITY, SORT, PARAMS);

        assertThat(cursor.cursor()).isEqualTo("12");
        assertThat(cursor.pageSize()).isEqualTo(34);
        assertThat(cursor.sort()).isEqualTo(SORT);
    }

}