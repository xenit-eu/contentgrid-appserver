package com.contentgrid.appserver.domain.paging.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.domain.paging.PageBasedPagination;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorContext;
import com.contentgrid.appserver.domain.paging.cursor.CursorCodec.CursorDecodeException;
import com.contentgrid.appserver.domain.paging.cursor.RequestIntegrityCheckCursorCodec.IntegrityCheckFailedException;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.data.SortData.FieldSort;
import com.contentgrid.hateoas.pagination.api.Pagination;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class RequestIntegrityCheckCursorCodecTest {

    private static final SortData UNSORTED = new SortData(List.of());

    public static Stream<Arguments> withoutCursorParams() {
        return Stream.of(
                Arguments.of(new CursorContext(null, 5, UNSORTED), "test", Map.of("a", "b"))
        );
    }

    public static Stream<Arguments> testParams() {
        return Stream.of(
                Arguments.of(new CursorContext("abc", 1, UNSORTED), "test", Map.of("a", "b")),
                Arguments.of(new CursorContext("abc", 1, UNSORTED), "test", Map.of()),
                Arguments.of(new CursorContext("ZZZ", 100, UNSORTED), "test", Map.of()),
                Arguments.of(new CursorContext("ZZZ", 100, new SortData(Collections.singletonList(new FieldSort(null, SortableName.of("abc"))))), "", Map.of())
        );
    }

    @ParameterizedTest
    @MethodSource({"withoutCursorParams", "testParams"})
    void generateAndVerify(CursorContext cursorContext, String entityName, Map<String, String> parameters) throws CursorDecodeException {
        CursorCodec mockCodec = Mockito.mock(CursorCodec.class);

        // pagination is always passed direct through to the delegate, so it's value is irrelevant
        var pagination = new PageBasedPagination(1, 1);
        var codec = new RequestIntegrityCheckCursorCodec(mockCodec);

        Mockito.when(mockCodec.encodeCursor(pagination, entityName, cursorContext.sort(), parameters)).thenReturn(cursorContext);

        var encodedContext = codec.encodeCursor(pagination, entityName, cursorContext.sort(), parameters);

        Mockito.when(mockCodec.decodeCursor(cursorContext, entityName, parameters)).thenReturn(pagination);

        var decodedPagination = codec.decodeCursor(encodedContext, entityName, parameters);

        assertThat(decodedPagination).isEqualTo(pagination);
    }

    @ParameterizedTest
    @MethodSource("testParams")
    void modifyData(CursorContext cursorContext, String entityName, Map<String, String> parameters) throws CursorDecodeException {
        CursorCodec mockCodec = Mockito.mock(CursorCodec.class);

        // pageable is always passed direct through to the delegate, so it's value is irrelevant
        var pageable = new PageBasedPagination(1, 1);
        var codec = new RequestIntegrityCheckCursorCodec(mockCodec);

        Mockito.when(mockCodec.encodeCursor(pageable, entityName, cursorContext.sort(), parameters)).thenReturn(cursorContext);

        var encodedContext = codec.encodeCursor(pageable, entityName, cursorContext.sort(), parameters);

        Mockito.when(mockCodec.decodeCursor(cursorContext, entityName, parameters)).thenReturn(pageable);

        // Different ways of messing with the input data
        // - Change query parameter
        assertThatThrownBy(() -> {
            var modified = new HashMap<>(parameters);
            modified.put("a", "c");
            codec.decodeCursor(encodedContext, entityName, modified);
        }).isInstanceOf(IntegrityCheckFailedException.class);

        // - Add query parameter
        assertThatThrownBy(() -> {
            var modified = new HashMap<>(parameters);
            modified.put("x", "y");
            codec.decodeCursor(encodedContext, entityName, modified);
        }).isInstanceOf(IntegrityCheckFailedException.class);

        // - Remove query parameter
        if (parameters.containsKey("a")) {
            assertThatThrownBy(() -> {
                var modified = new HashMap<>(parameters);
                modified.remove("a");
                codec.decodeCursor(encodedContext, entityName, modified);
            }).isInstanceOf(IntegrityCheckFailedException.class);
        }

        // - Change path (entity)
        assertThatThrownBy(() -> {
            var modified = "xxx";
            codec.decodeCursor(encodedContext, modified, parameters);
        }).isInstanceOf(IntegrityCheckFailedException.class);

        // - Change page size
        assertThatThrownBy(() -> {
            var modifiedContext = new CursorContext(encodedContext.cursor(), encodedContext.pageSize() + 1,
                    encodedContext.sort());
            codec.decodeCursor(modifiedContext, entityName, parameters);
        }).isInstanceOf(IntegrityCheckFailedException.class);

        // - Append to cursor
        assertThatThrownBy(() -> {
            var modifiedContext = new CursorContext(encodedContext.cursor() + "ZZZ", encodedContext.pageSize(),
                    encodedContext.sort());
            codec.decodeCursor(modifiedContext, entityName, parameters);
        }).isInstanceOf(IntegrityCheckFailedException.class);

        // - Remove part from cursor
        assertThatThrownBy(() -> {
            var modifiedContext = new CursorContext(encodedContext.cursor().substring(5), encodedContext.pageSize(),
                    encodedContext.sort());
            codec.decodeCursor(modifiedContext, entityName, parameters);
        }).isInstanceOf(IntegrityCheckFailedException.class);

        // - Change sorting
        assertThatThrownBy(() -> {
            var modifiedSortFields = new ArrayList<>(encodedContext.sort().getSortedFields());
            modifiedSortFields.add(new FieldSort(null, SortableName.of("field")));
            var modifiedSort = new SortData(modifiedSortFields);
            var modifiedContext = new CursorContext(encodedContext.cursor(), encodedContext.pageSize(), modifiedSort);
            codec.decodeCursor(modifiedContext, entityName, parameters);
        }).isInstanceOf(IntegrityCheckFailedException.class);

        // - Remove sorting
        if (!encodedContext.sort().getSortedFields().isEmpty()) {
            assertThatThrownBy(() -> {
                var modifiedContext = new CursorContext(encodedContext.cursor(), encodedContext.pageSize(),
                        UNSORTED);
                codec.decodeCursor(modifiedContext, entityName, parameters);
            }).isInstanceOf(IntegrityCheckFailedException.class);
        }
    }

    @Test
    void extended_whenShortCrc() {
        CursorCodec codec = new RequestIntegrityCheckCursorCodec(new CursorCodec() {
            @Override
            public Pagination decodeCursor(CursorContext context, String entityName, Map<String, String> parameters) {
                throw new UnsupportedOperationException("Test implementation can not decode cursors");
            }

            @Override
            public CursorContext encodeCursor(Pagination pagination, String entityName, SortData sort, Map<String, String> parameters) {
                return CursorContext.builder()
                        .cursor("1")
                        .pageSize(pagination.getLimit())
                        .sort(sort)
                        .build();
            }
        });

        CursorCodec.CursorContext context = null;

        for (int i = 0; i < 10_000; i++) {
            var parameters = Map.of("t", Integer.toString(i));

            context = codec.encodeCursor(new PageBasedPagination(10, 1), "suppliers", new SortData(List.of()), parameters);

            if (context.cursor().startsWith("0")) {
                break;
            }
        }

        assertThat(context)
                .isNotNull()
                .extracting(CursorContext::cursor)
                .asString()
                .startsWith("0");

    }
}