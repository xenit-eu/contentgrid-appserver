package com.contentgrid.appserver.domain.data.type;

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
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
final class DataEntryDataType {

    private static final Map<Class<? extends DataEntry>, DataType> MAPPING = HashMap.newHashMap(10);

    static {
        MAPPING.put(DataEntry.StringDataEntry.class, TechnicalDataType.STRING);
        MAPPING.put(DataEntry.BooleanDataEntry.class, TechnicalDataType.BOOLEAN);
        MAPPING.put(LongDataEntry.class, TechnicalDataType.LONG);
        MAPPING.put(DecimalDataEntry.class, TechnicalDataType.DECIMAL);
        MAPPING.put(DataEntry.NullDataEntry.class, TechnicalDataType.NULL);
        MAPPING.put(DataEntry.MapDataEntry.class, ObjectDataType.simple());
        MAPPING.put(DataEntry.ListDataEntry.class, TechnicalDataType.LIST);
        MAPPING.put(DataEntry.RelationDataEntry.class, RelationDataType.simple());
        MAPPING.put(DataEntry.MultipleRelationDataEntry.class, RelationListDataType.simple());
        MAPPING.put(DataEntry.FileDataEntry.class, TechnicalDataType.CONTENT);
        MAPPING.put(DataEntry.InstantDataEntry.class, TechnicalDataType.DATETIME);
        MAPPING.put(MissingDataEntry.class, TechnicalDataType.MISSING);
    }

    public static DataType of(Class<? extends DataEntry> clazz) {
        var result = MAPPING.get(clazz);
        if(result == null) {
            throw new IllegalArgumentException("No DataType for %s".formatted(clazz));
        }
        return result;
    }

    public static DataType of(DataEntry dataEntry) {
        return dataEntry.map(new DataEntryTransformer<>() {
            @Override
            public DataType transform(RelationDataEntry relationDataEntry) {
                return RelationDataType.to(relationDataEntry.getTargetEntity());
            }

            @Override
            public DataType transform(MultipleRelationDataEntry multipleRelationDataEntry) {
                return RelationListDataType.to(multipleRelationDataEntry.getTargetEntity());
            }

            @Override
            public DataType transform(FileDataEntry fileDataEntry) {
                return TechnicalDataType.CONTENT;
            }

            @Override
            public DataType transform(StringDataEntry stringDataEntry) {
                return TechnicalDataType.STRING;
            }

            @Override
            public DataType transform(LongDataEntry numberDataEntry) {
                return TechnicalDataType.LONG;
            }

            @Override
            public DataType transform(DecimalDataEntry decimalDataEntry) {
                return TechnicalDataType.DECIMAL;
            }

            @Override
            public DataType transform(BooleanDataEntry booleanDataEntry) {
                return TechnicalDataType.BOOLEAN;
            }

            @Override
            public DataType transform(NullDataEntry nullDataEntry) {
                return TechnicalDataType.NULL;
            }

            @Override
            public DataType transform(InstantDataEntry instantDataEntry) {
                return TechnicalDataType.DATETIME;
            }

            @Override
            public DataType transform(ListDataEntry listDataEntry) {
                return TechnicalDataType.LIST;
            }

            @Override
            public DataType transform(MapDataEntry mapDataEntry) {
                return ObjectDataType.of(mapDataEntry);
            }

            @Override
            public DataType transform(MissingDataEntry missingDataEntry) {
                return TechnicalDataType.MISSING;
            }
        });
    }


}
