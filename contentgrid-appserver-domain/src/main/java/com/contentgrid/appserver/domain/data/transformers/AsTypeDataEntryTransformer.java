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
import com.contentgrid.appserver.domain.data.transformers.result.Result;
import com.contentgrid.appserver.domain.data.type.DataType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AsTypeDataEntryTransformer<T extends DataEntry> implements DataEntryTransformer<Result<T>> {

    @NonNull
    protected final Class<T> expectedType;

    private Result<T> assertType(DataEntry dataEntry) {
        if (expectedType.isInstance(dataEntry)) {
            return Result.of((T) dataEntry);
        }
        return Result.error(
                new InvalidDataTypeException(
                        DataType.of(expectedType),
                        DataType.of(dataEntry)
                )
        );
    }

    @Override
    public Result<T> transform(RelationDataEntry relationDataEntry) {
        return assertType(relationDataEntry);
    }

    @Override
    public Result<T> transform(MultipleRelationDataEntry multipleRelationDataEntry) {
        return assertType(multipleRelationDataEntry);
    }

    @Override
    public Result<T> transform(FileDataEntry fileDataEntry) {
        return assertType(fileDataEntry);
    }

    @Override
    public Result<T> transform(ListDataEntry listDataEntry) {
        return assertType(listDataEntry);
    }

    @Override
    public Result<T> transform(MapDataEntry mapDataEntry) {
        return assertType(mapDataEntry);
    }

    @Override
    public Result<T> transform(MissingDataEntry missingDataEntry) {
        return assertType(missingDataEntry);
    }

    @Override
    public Result<T> transform(StringDataEntry stringDataEntry) {
        return assertType(stringDataEntry);
    }

    @Override
    public Result<T> transform(LongDataEntry numberDataEntry) {
        return assertType(numberDataEntry);
    }

    @Override
    public Result<T> transform(DecimalDataEntry decimalDataEntry) {
        return assertType(decimalDataEntry);
    }

    @Override
    public Result<T> transform(BooleanDataEntry booleanDataEntry) {
        return assertType(booleanDataEntry);
    }

    @Override
    public Result<T> transform(NullDataEntry nullDataEntry) {
        return assertType(nullDataEntry);
    }

    @Override
    public Result<T> transform(InstantDataEntry instantDataEntry) {
        return assertType(instantDataEntry);
    }


}
