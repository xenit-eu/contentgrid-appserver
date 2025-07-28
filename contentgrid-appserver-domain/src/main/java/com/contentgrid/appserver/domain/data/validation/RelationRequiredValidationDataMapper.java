package com.contentgrid.appserver.domain.data.validation;

import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.mapper.RelationMapper;
import java.util.Optional;

/**
 * Validation mapper that enforces required relation constraints.
 * <p>
 * This mapper validates that required relations are not null or missing.
 * If a relation is marked as required but the input data is null or missing, 
 * it throws a {@link RequiredConstraintViolationInvalidDataException}.
 */
public class RelationRequiredValidationDataMapper implements RelationMapper<DataEntry, Optional<DataEntry>> {

    @Override
    public Optional<DataEntry> mapRelation(Relation relation, DataEntry inputData) throws InvalidPropertyDataException {
        if (!relation.getSourceEndPoint().isRequired()) {
            return Optional.of(inputData);
        }
        if (inputData instanceof NullDataEntry || inputData instanceof MissingDataEntry) {
            throw new RequiredConstraintViolationInvalidDataException()
                    .withinProperty(relation.getSourceEndPoint().getName());
        }
        return Optional.of(inputData);
    }
}
