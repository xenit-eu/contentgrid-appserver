package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.DataEntry.AnyRelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;

/**
 * Base interface for any kind of supported data value
 */
public sealed interface DataEntry permits AnyRelationDataEntry, FileDataEntry, PlainDataEntry {

    /**
     * A plain data value; a value trivially representable in structured input (e.g. JSON).
     * <p>
     * This includes all scalar values, lists, maps and omitted data
     */
    sealed interface PlainDataEntry extends DataEntry permits ListDataEntry, MapDataEntry, MissingDataEntry,
            ScalarDataEntry {

    }

    /**
     * A scalar data value: the basic types boolean, decimal, instant, long, null and string
     */
    sealed interface ScalarDataEntry extends PlainDataEntry permits BooleanDataEntry, DecimalDataEntry,
            InstantDataEntry, LongDataEntry, NullDataEntry, StringDataEntry {

        Object getValue();

    }

    /**
     * A relation data value: references one or more entity instances
     */
    sealed interface AnyRelationDataEntry extends DataEntry permits RelationDataEntry, MultipleRelationDataEntry {
        EntityName getTargetEntity();

    }

    @Value
    class StringDataEntry implements ScalarDataEntry {

        @NonNull
        String value;

    }

    @Value
    class LongDataEntry implements ScalarDataEntry {

        @NonNull
        Long value;

    }

    @Value
    class DecimalDataEntry implements ScalarDataEntry {

        @NonNull
        BigDecimal value;

    }

    @Value
    class InstantDataEntry implements ScalarDataEntry {

        @NonNull
        Instant value;

    }

    @Value
    class BooleanDataEntry implements ScalarDataEntry {

        boolean value;

        @Override
        public Boolean getValue() {
            return value;
        }

    }

    /**
     * Represents values that are explicitly set to {@code null}.
     *
     * @see MissingDataEntry which is used for representing values that are not present at all
     */
    @Value
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    class NullDataEntry implements ScalarDataEntry {

        public static final NullDataEntry INSTANCE = new NullDataEntry();

        @Override
        public Object getValue() {
            return null;
        }

    }

    /**
     * Represents a value that is not present at all (e.g. the value of a key that is not present)
     * 
     * @see NullDataEntry which is used for values that are explicitly set to {@code null}
     */
    @Value
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    class MissingDataEntry implements PlainDataEntry {

        public static final MissingDataEntry INSTANCE = new MissingDataEntry();

    }

    @Value
    @Builder
    @RequiredArgsConstructor
    class ListDataEntry implements PlainDataEntry {

        @NonNull
        @Singular
        List<PlainDataEntry> items;

    }

    @Value
    @Builder
    @RequiredArgsConstructor
    class MapDataEntry implements PlainDataEntry {

        @NonNull
        @Singular
        Map<String, PlainDataEntry> items;

        public boolean containsKey(String key) {
            return items.containsKey(key);
        }

        public PlainDataEntry get(String key) {
            return items.getOrDefault(key, MissingDataEntry.INSTANCE);
        }

        public int size() {
            return items.size();
        }
    }

    @Value
    class RelationDataEntry implements AnyRelationDataEntry {

        @NonNull
        EntityName targetEntity;

        @NonNull
        EntityId targetId;

    }

    @Value
    @Builder
    @RequiredArgsConstructor
    class MultipleRelationDataEntry implements AnyRelationDataEntry {

        @NonNull
        EntityName targetEntity;

        @NonNull
        @Singular
        List<EntityId> targetIds;

    }

    @Value
    class FileDataEntry implements DataEntry {

        String filename;
        String contentType;

        long size;
        @Getter(value = AccessLevel.NONE)
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        @NonNull
        InputStreamSupplier inputStream;

        public InputStream getInputStream() throws IOException {
            return inputStream.getInputStream();
        }

        public interface InputStreamSupplier {
            InputStream getInputStream() throws IOException;
        }
    }
}
