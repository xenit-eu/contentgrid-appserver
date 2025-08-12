package com.contentgrid.appserver.domain.data.mapper;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.validation.ValidationExceptionCollector;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Aggregator mapper that processes all attributes and relations from {@link RequestInputData} into {@link AttributeData} and {@link RelationData}
 * <p>
 * The underlying {@link AttributeMapper} and {@link RelationMapper} are used to perform the actual mapping and validation of attributes and relations.
 * <p>
 * Validation errors are collected for all attributes and relations, and rethrown so all validation errors can be reported to the user at once
 */
@RequiredArgsConstructor
public class RequestInputDataMapper {
    private final List<Attribute> attributes;
    private final Set<Relation> relations;

    private final AttributeMapper<RequestInputData, Optional<AttributeData>> attributeMapper;

    private final RelationMapper<RequestInputData, Optional<RelationData>> relationMapper;


    public List<AttributeData> mapAttributes(RequestInputData requestInputData) throws InvalidPropertyDataException {
        var data = new ArrayList<AttributeData>(attributes.size());
        var exceptionCollector = new ValidationExceptionCollector<>(InvalidPropertyDataException.class);
        for (var attribute : attributes) {
            exceptionCollector.use(() -> attributeMapper.mapAttribute(attribute, requestInputData)
                    .ifPresent(data::add));
        }
        exceptionCollector.rethrow();
        return data;
    }

    public List<RelationData> mapRelations(RequestInputData requestInputData) throws InvalidPropertyDataException {
        var data = new ArrayList<RelationData>(relations.size());
        var exceptionCollector = new ValidationExceptionCollector<>(InvalidPropertyDataException.class);
        for (var relation : relations) {
            // Relations that are hidden on this side can't be mapped from input at all, so skip them immediately
            if(relation.getSourceEndPoint().hasFlag(HiddenEndpointFlag.class)) {
                continue;
            }
            exceptionCollector.use(() -> relationMapper.mapRelation(relation, requestInputData)
                    .ifPresent(data::add));
        }
        exceptionCollector.rethrow();
        return data;
    }

}
