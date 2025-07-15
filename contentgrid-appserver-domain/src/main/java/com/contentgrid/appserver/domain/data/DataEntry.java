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

    <T> T map(DataEntryTransformer<T> transformer);

    /**
     * A plain data value; a value trivially representable in structured input (e.g. JSON).
     * <p>
     * This includes all scalar values, lists, maps and omitted data
     */
    sealed interface PlainDataEntry extends DataEntry permits ListDataEntry, MapDataEntry, MissingDataEntry,
            ScalarDataEntry {

        @Override
        default <T> T map(DataEntryTransformer<T> transformer) {
            return map((PlainDataEntryTransformer<T>) transformer);
        }

        <T> T map(PlainDataEntryTransformer<T> transformer);
    }

    /**
     * A scalar data value: the basic types boolean, decimal, instant, long, null and string
     */
    sealed interface ScalarDataEntry extends PlainDataEntry permits BooleanDataEntry, DecimalDataEntry,
            InstantDataEntry, LongDataEntry, NullDataEntry, StringDataEntry {

        Object getValue();

        @Override
        default <T> T map(PlainDataEntryTransformer<T> transformer) {
            return map((ScalarDataEntryTransformer<T>) transformer);
        }

        <T> T map(ScalarDataEntryTransformer<T> transformer);
    }

    /**
     * A relation data value: references one or more entity instances
     */
    sealed interface AnyRelationDataEntry extends DataEntry permits RelationDataEntry, MultipleRelationDataEntry {
        EntityName getTargetEntity();

        @Override
        default <T> T map(DataEntryTransformer<T> transformer) {
            return map((AnyRelationDataEntryTransformer<T>) transformer);
        }

        <T> T map(AnyRelationDataEntryTransformer<T> transformer);
    }

    @Value
    class StringDataEntry implements ScalarDataEntry {

        @NonNull
        String value;

        @Override
        public <T> T map(ScalarDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }
    }

    @Value
    class LongDataEntry implements ScalarDataEntry {

        @NonNull
        Long value;

        @Override
        public <T> T map(ScalarDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }
    }

    @Value
    class DecimalDataEntry implements ScalarDataEntry {

        @NonNull
        BigDecimal value;

        @Override
        public <T> T map(ScalarDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }
    }

    @Value
    class InstantDataEntry implements ScalarDataEntry {

        @NonNull
        Instant value;

        @Override
        public <T> T map(ScalarDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }
    }

    @Value
    class BooleanDataEntry implements ScalarDataEntry {

        boolean value;

        @Override
        public Boolean getValue() {
            return value;
        }

        @Override
        public <T> T map(ScalarDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
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

        @Override
        public <T> T map(ScalarDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
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

        @Override
        public <T> T map(PlainDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }
    }

    @Value
    @Builder
    @RequiredArgsConstructor
    class ListDataEntry implements PlainDataEntry {

        @NonNull
        @Singular
        List<PlainDataEntry> items;

        @Override
        public <T> T map(PlainDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }

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

        @Override
        public <T> T map(PlainDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
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

        @Override
        public <T> T map(AnyRelationDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }
    }

    @Value
    class MultipleRelationDataEntry implements AnyRelationDataEntry {

        @NonNull
        EntityName targetEntity;

        @NonNull
        List<EntityId> targetIds;

        @Override
        public <T> T map(AnyRelationDataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }
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

        @Override
        public <T> T map(DataEntryTransformer<T> transformer) {
            return transformer.transform(this);
        }

        public interface InputStreamSupplier {
            InputStream getInputStream() throws IOException;
        }
    }
}
