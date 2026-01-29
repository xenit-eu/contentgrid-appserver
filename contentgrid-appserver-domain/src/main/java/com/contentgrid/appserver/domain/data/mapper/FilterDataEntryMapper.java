package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.DefaultValueFlag;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.values.DataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.MapDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.NullDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.ScalarDataEntry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * Mapper that can filter out attribute and relation {@link DataEntry} based on their values
 */
@RequiredArgsConstructor
public abstract class FilterDataEntryMapper implements AttributeAndRelationMapper<DataEntry, Optional<DataEntry>, DataEntry, Optional<DataEntry>> {
    @Override
    public Optional<DataEntry> mapAttribute(Attribute attribute, DataEntry inputData) {

        if (attribute instanceof SimpleAttribute simp) {
            return transformNested(inputData, simp.getDefaultValue());
        }

        return transformNested(inputData, NullDataEntry.INSTANCE);
    }

    @Override
    public Optional<DataEntry> mapRelation(Relation relation, DataEntry inputData) {
        return transformNested(inputData, NullDataEntry.INSTANCE);
    }

    private Optional<DataEntry> transformNested(DataEntry inputData, ScalarDataEntry defaultValue) {
        return switch (inputData) {
            case MapDataEntry mapDataEntry -> {
                var builder = MapDataEntry.builder();
                for (var entry : mapDataEntry.getItems().entrySet()) {
                    transformNested(entry.getValue(), defaultValue)
                            .ifPresent(newValue -> builder.item(entry.getKey(), (PlainDataEntry) newValue));
                }
                yield Optional.of(builder.build());
            }
            default -> transform(inputData, defaultValue);
        };
    }

    abstract protected Optional<DataEntry> transform(DataEntry inputData, ScalarDataEntry defaultValue);

    public static FilterDataEntryMapper omitMissing() {
        return new FilterDataEntryMapper() {
            @Override
            protected Optional<DataEntry> transform(DataEntry inputData, ScalarDataEntry defaultValue) {
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
            protected Optional<DataEntry> transform(DataEntry inputData, ScalarDataEntry defaultValue) {
                if (inputData instanceof MissingDataEntry) {
                    // defaultValue will be NullDataEntry if there's no default
                    return Optional.of(defaultValue);
                }
                return Optional.of(inputData);
            }
        };
    }
}


