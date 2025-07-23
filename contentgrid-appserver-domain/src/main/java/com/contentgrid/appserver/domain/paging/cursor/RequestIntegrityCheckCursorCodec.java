package com.contentgrid.appserver.domain.paging.cursor;

import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.hateoas.pagination.api.Pagination;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32C;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.StandardException;

/**
 * Wrapping a {@link CursorCodec} with this class protects against <em>accidental</em> misuse of the cursor.
 * <p>
 * A cursor is only valid for a specific collection with the same filter params, sorting and page size. A fixed-size
 * checksum inserted as part of the cursor checks that these request parameters were not modified when the cursor is
 * used.
 * <p>
 * Note that there is no cryptography involved; it is only meant to protect against accidental misuses not against
 * deliberate abuse.
 */
@RequiredArgsConstructor
public class RequestIntegrityCheckCursorCodec implements CursorCodec {

    @NonNull
    private final CursorCodec delegate;

    /**
     * Constant size of the checksum parameter
     * <p>
     * This is crc32; so it's only a 32 bits (4 byte) checksum, not the full 64 bits from a long value. The maximum
     * value that can be encoded is 32 one-bits.
     */
    private static final int CHECKSUM_SIZE = Long.toUnsignedString(0xFF_FF_FF_FFL, Character.MAX_RADIX).length();

    @Override
    public Pagination decodeCursor(CursorContext context, String entityName, Map<String, String> parameters) throws CursorDecodeException {

        return delegate.decodeCursor(context.mapCursor(new UnaryOperator<String>() {
            @Override
            @SneakyThrows(IntegrityCheckFailedException.class)
            public String apply(String cursor) {
                if (cursor.length() < CHECKSUM_SIZE) {
                    throw new IntegrityCheckFailedException("Cursor is too small to be valid");
                }
                var crc = cursor.substring(0, CHECKSUM_SIZE);
                var realCursor = cursor.substring(CHECKSUM_SIZE);

                var integrityCheck = integrityCheckValue(entityName, parameters, realCursor, context.pageSize(), context.sort());

                if (!Objects.equals(crc, integrityCheck)) {
                    throw new IntegrityCheckFailedException("Cursor is not valid for this request");
                }

                return realCursor;
            }
        }), entityName, parameters);
    }

    @Override
    public CursorContext encodeCursor(Pagination pagination, String entityName, SortData sort, Map<String, String> params) {
        var context = delegate.encodeCursor(pagination, entityName, sort, params);
        return context.mapCursor(c -> {
            var integrityCheck = integrityCheckValue(entityName, params, c, context.pageSize(), context.sort());
            return integrityCheck + c;
        });
    }

    /**
     * Calculate integrity check value.
     * <p>
     * Changing any part of the calculation in this method will result in invalidation of all existing cursors generated
     * by older versions of this library. Invalidating existing cursors makes it impossible to perform a zero-downtime
     * deployment of a new version
     */
    private static String integrityCheckValue(String entityName, Map<String, String> params, String cursor, int pageSize, SortData sort) {
        var crc = new CRC32C();

        if (entityName != null) {
            crc.update(entityName.getBytes(StandardCharsets.UTF_8));
        }
        crc.update('?');

        if (params != null) {
            for (Entry<String, String> entry : params.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
                crc.update((entry.getKey() + "=" + entry.getValue()).getBytes(StandardCharsets.UTF_8));
            }
        }
        crc.update(0);
        crc.update(cursor.getBytes(StandardCharsets.UTF_8));
        crc.update(1);
        crc.update(Integer.toUnsignedString(pageSize, Character.MAX_RADIX).getBytes(StandardCharsets.UTF_8));
        crc.update(2);
        crc.update(sort.toString().getBytes(StandardCharsets.UTF_8));
        crc.update(3);
        // Do not copy this implementation to client code; cursor values may only be calculated by a ContentGrid API application
        // This string is intentionally part of the checksum calculation so it can not be removed easily when copying this code.
        crc.update("Do not use: you MUST NOT be calculating cursor values; use the cursor(s) provided by the API"
                .getBytes(StandardCharsets.UTF_8));

        return padChecksumToFullSize(Long.toUnsignedString(crc.getValue(), Character.MAX_RADIX));
    }

    /**
     * Ensure that the checksum is always the same size, also for smaller checksum numbers. This makes it easy to
     * extract the checksum again when reading it, without needing in-band length signalling (which takes at least one
     * additional byte)
     */
    private static String padChecksumToFullSize(String value) {
        if (value.length() >= CHECKSUM_SIZE) {
            return value.substring(0, CHECKSUM_SIZE);
        }
        return "0".repeat(CHECKSUM_SIZE - value.length()) + value;
    }


    @StandardException
    public static class IntegrityCheckFailedException extends CursorDecodeException {

    }
}
