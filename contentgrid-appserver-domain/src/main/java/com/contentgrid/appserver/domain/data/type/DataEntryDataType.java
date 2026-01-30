package com.contentgrid.appserver.domain.data.type;

import com.contentgrid.appserver.application.model.values.DataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.BooleanDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.DecimalDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.FileDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.InstantDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.ListDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.LocalDateDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.LongDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.MapDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.MultipleRelationDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.NullDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.application.model.values.DataEntry.StringDataEntry;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
final class DataEntryDataType {

    private static final Map<Class<? extends DataEntry>, DataType> MAPPING = HashMap.newHashMap(10);

    static {
        MAPPING.put(StringDataEntry.class, TechnicalDataType.STRING);
        MAPPING.put(BooleanDataEntry.class, TechnicalDataType.BOOLEAN);
        MAPPING.put(LongDataEntry.class, TechnicalDataType.LONG);
        MAPPING.put(DecimalDataEntry.class, TechnicalDataType.DECIMAL);
        MAPPING.put(NullDataEntry.class, TechnicalDataType.NULL);
        MAPPING.put(MapDataEntry.class, ObjectDataType.simple());
        MAPPING.put(ListDataEntry.class, TechnicalDataType.LIST);
        MAPPING.put(RelationDataEntry.class, RelationDataType.simple());
        MAPPING.put(MultipleRelationDataEntry.class, RelationListDataType.simple());
        MAPPING.put(FileDataEntry.class, TechnicalDataType.CONTENT);
        MAPPING.put(LocalDateDataEntry.class, TechnicalDataType.DATE);
        MAPPING.put(InstantDataEntry.class, TechnicalDataType.DATETIME);
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
        return switch (dataEntry) {
            case RelationDataEntry relationDataEntry -> RelationDataType.to(relationDataEntry.getTargetEntity());
            case MultipleRelationDataEntry multipleRelationDataEntry -> RelationDataType.to(multipleRelationDataEntry.getTargetEntity());
            case MapDataEntry mapDataEntry -> ObjectDataType.of(mapDataEntry);
            case ListDataEntry ignored -> TechnicalDataType.LIST;
            case StringDataEntry ignored -> TechnicalDataType.STRING;
            case FileDataEntry ignored -> TechnicalDataType.CONTENT;
            case BooleanDataEntry ignored -> TechnicalDataType.BOOLEAN;
            case DecimalDataEntry ignored -> TechnicalDataType.DECIMAL;
            case LocalDateDataEntry ignored -> TechnicalDataType.DATE;
            case InstantDataEntry ignored -> TechnicalDataType.DATETIME;
            case LongDataEntry ignored -> TechnicalDataType.LONG;
            case MissingDataEntry ignored -> TechnicalDataType.MISSING;
            case NullDataEntry ignored -> TechnicalDataType.NULL;
        };
    }


}
