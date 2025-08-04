package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * Mapper that can filter out attribute and relation {@link DataEntry} based on their values
 */
@RequiredArgsConstructor
public abstract class FilterDataEntryMapper implements AttributeAndRelationMapper<DataEntry, Optional<DataEntry>, DataEntry, Optional<DataEntry>> {
    @Override
    public Optional<DataEntry> mapAttribute(Attribute attribute, DataEntry inputData) {
        return transformNested(inputData);
    }

    @Override
    public Optional<DataEntry> mapRelation(Relation relation, DataEntry inputData) {
        return transformNested(inputData);
    }

    private Optional<DataEntry> transformNested(DataEntry inputData) {
        return switch (inputData) {
            case MapDataEntry mapDataEntry -> {
                var builder = MapDataEntry.builder();
                for (var entry : mapDataEntry.getItems().entrySet()) {
                    transformNested(entry.getValue())
                            .ifPresent(newValue -> builder.item(entry.getKey(), (PlainDataEntry) newValue));
                }
                yield Optional.of(builder.build());
            }
            default -> transform(inputData);
        };
    }

    abstract protected Optional<DataEntry> transform(DataEntry inputData);

    public static FilterDataEntryMapper omitMissing() {
        return new FilterDataEntryMapper() {
            @Override
            protected Optional<DataEntry> transform(DataEntry inputData) {
                if(inputData instanceof MissingDataEntry) {
                    return Optional.empty();
                }
                return Optional.of(inputData);
            }
        };
    }

    public static FilterDataEntryMapper missingAsNull() {
        return new FilterDataEntryMapper() {
            @Override
            protected Optional<DataEntry> transform(DataEntry inputData) {
                if(inputData instanceof MissingDataEntry) {
                    return Optional.of(NullDataEntry.INSTANCE);
                }
                return Optional.of(inputData);
            }
        };
    }
}


