package com.contentgrid.appserver.domain.data.transformers;

import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MultipleRelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.DataEntryTransformer;
import java.util.Optional;

public class FilterDataEntryTransformer implements DataEntryTransformer<Optional<DataEntry>> {

    public static FilterDataEntryTransformer omitMissing() {
        return new FilterDataEntryTransformer() {
            @Override
            public Optional<DataEntry> transform(MissingDataEntry missingDataEntry) {
                return Optional.empty();
            }
        };
    }

    public static FilterDataEntryTransformer missingAsNull() {
        return new FilterDataEntryTransformer() {
            @Override
            public Optional<DataEntry> transform(MissingDataEntry missingDataEntry) {
                return Optional.of(NullDataEntry.INSTANCE);
            }
        };
    }

    @Override
    public Optional<DataEntry> transform(RelationDataEntry relationDataEntry) {
        return Optional.of(relationDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(MultipleRelationDataEntry multipleRelationDataEntry) {
        return Optional.of(multipleRelationDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(FileDataEntry fileDataEntry) {
        return Optional.of(fileDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(ListDataEntry listDataEntry) {
        return Optional.of(listDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(MapDataEntry mapDataEntry) {
        return Optional.of(mapDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(MissingDataEntry missingDataEntry) {
        return Optional.of(missingDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(StringDataEntry stringDataEntry) {
        return Optional.of(stringDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(LongDataEntry numberDataEntry) {
        return Optional.of(numberDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(DecimalDataEntry decimalDataEntry) {
        return Optional.of(decimalDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(BooleanDataEntry booleanDataEntry) {
        return Optional.of(booleanDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(NullDataEntry nullDataEntry) {
        return Optional.of(nullDataEntry);
    }

    @Override
    public Optional<DataEntry> transform(InstantDataEntry instantDataEntry) {
        return Optional.of(instantDataEntry);
    }
}
