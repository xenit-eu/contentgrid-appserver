package com.contentgrid.appserver.domain.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.DatamodelApiImpl.ResponseOutputDataMapper;
import com.contentgrid.appserver.domain.data.mapper.AttributeDataToDataEntryMapper;
import com.contentgrid.appserver.query.engine.api.EventConsumer;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DomainEventDispatcher implements EventConsumer {
    private final EntityFormatter formatter;
    private final FormattedEventDispatcher rabbitMqEventDispatcher;

    public DomainEventDispatcher(EntityFormatter formatter) {
        this(formatter, null);
    }

    public DomainEventDispatcher(EntityFormatter formatter, FormattedEventDispatcher formattedEventDispatcher) {
        this.formatter = formatter;
        this.rabbitMqEventDispatcher = formattedEventDispatcher;
    }

    @Override
    public void dispatchCreate(Application application, EntityName entity, EntityData data) {
        log.debug("=== CREATE === [{}]: {}", entity, data);

        var ent = application.getRequiredEntityByName(entity);
        var mapper = new ResponseOutputDataMapper(
                ent.getAttributes(),
                new AttributeDataToDataEntryMapper()
        );

        var entityInstance = mapper.mapAttributes(data);
        var jsonNode = formatter.format(application, entity, entityInstance);
        if (rabbitMqEventDispatcher != null) {
            rabbitMqEventDispatcher.dispatchCreate(entity.getValue(), jsonNode);
        }
    }

    @Override
    public void dispatchUpdate(Application application, EntityName entity, EntityData oldData, EntityData newData) {
        log.debug("=== UPDATE === [{}] Was:\n{}\nBecomes:\n{}", entity, oldData, newData);

        var ent = application.getRequiredEntityByName(entity);
        var mapper = new ResponseOutputDataMapper(
                ent.getAttributes(),
                new AttributeDataToDataEntryMapper()
        );

        var oldInstance = mapper.mapAttributes(oldData);
        var newInstance = mapper.mapAttributes(newData);

        var oldJson = formatter.format(application, entity, oldInstance);
        var newJson = formatter.format(application, entity, newInstance);

        if (rabbitMqEventDispatcher != null) {
            rabbitMqEventDispatcher.dispatchUpdate(entity.getValue(), oldJson, newJson);
        }
    }

    @Override
    public void dispatchDelete(Application application, EntityName entity, EntityData oldData) {
        log.debug("=== DELETE === [{}]: {}", entity, oldData);

        var ent = application.getRequiredEntityByName(entity);
        var mapper = new ResponseOutputDataMapper(
                ent.getAttributes(),
                new AttributeDataToDataEntryMapper()
        );

        var oldInstance = mapper.mapAttributes(oldData);
        var oldJson = formatter.format(application, entity, oldInstance);

        if (rabbitMqEventDispatcher != null) {
            rabbitMqEventDispatcher.dispatchDelete(entity.getValue(), oldJson);
        }
    }
}
