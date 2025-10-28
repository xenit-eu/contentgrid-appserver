package com.contentgrid.appserver.domain.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.DatamodelApiImpl.ResponseOutputDataMapper;
import com.contentgrid.appserver.domain.data.mapper.AttributeDataToDataEntryMapper;
import com.contentgrid.appserver.query.engine.api.EventConsumer;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DomainEventDispatcher implements EventConsumer {
    private final EntityFormatter formatter;

    @Override
    public void dispatchCreate(Application application, EntityName entity, EntityData data) {
        log.info("=== CREATE === [{}]: {}", entity, data);

        var ent = application.getRequiredEntityByName(entity);
        var mapper = new ResponseOutputDataMapper(
                ent.getAttributes(),
                new AttributeDataToDataEntryMapper()
        );

        var entityInstance = mapper.mapAttributes(data);
        var jsonNode = formatter.format(application, entity, entityInstance);
        log.info("[[[ JSON ]]]\n{}", jsonNode);
    }

    @Override
    public void dispatchUpdate(Application application, EntityName entity, EntityData oldData, EntityData newData) {
        log.info("=== UPDATE === [{}] Was:\n{}\nBecomes:\n{}", entity, oldData, newData);
    }

    @Override
    public void dispatchDelete(Application application, EntityName entity, EntityData oldData) {
        log.info("=== DELETE === [{}]: {}", entity, oldData);
    }
}
