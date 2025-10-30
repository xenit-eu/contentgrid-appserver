package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.events.EntityFormatter;
import com.contentgrid.appserver.domain.events.FormattedEventDispatcher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DomainEventDispatcher {
    private final EntityFormatter formatter;
    private final FormattedEventDispatcher rabbitMqEventDispatcher;

    public DomainEventDispatcher(EntityFormatter formatter, FormattedEventDispatcher formattedEventDispatcher) {
        this.formatter = formatter;
        this.rabbitMqEventDispatcher = formattedEventDispatcher;
    }

    public void dispatchCreate(Application application, InternalEntityInstance instance) {
        var entity = instance.getIdentity().getEntityName();
        log.debug("=== CREATE === [{}]: {}", entity, instance);

        if (formatter != null) {
            var jsonNode = formatter.format(application, entity, instance);
            if (rabbitMqEventDispatcher != null) {
                rabbitMqEventDispatcher.dispatchCreate(entity.getValue(), jsonNode);
            }
        }
    }

    public void dispatchUpdate(Application application, InternalEntityInstance oldInstance,
            InternalEntityInstance newInstance) {
        var entity = oldInstance.getIdentity().getEntityName();
        log.debug("=== UPDATE === [{}] Was:\n{}\nBecomes:\n{}", entity, oldInstance, newInstance);

        if (formatter != null) {
            var oldJson = formatter.format(application, entity, oldInstance);
            var newJson = formatter.format(application, entity, newInstance);

            if (rabbitMqEventDispatcher != null) {
                rabbitMqEventDispatcher.dispatchUpdate(entity.getValue(), oldJson, newJson);
            }
        }
    }

    public void dispatchDelete(Application application, InternalEntityInstance instance) {
        var entity = instance.getIdentity().getEntityName();
        log.debug("=== DELETE === [{}]: {}", entity, instance);

        if (formatter != null) {
            var jsonNode = formatter.format(application, entity, instance);

            if (rabbitMqEventDispatcher != null) {
                rabbitMqEventDispatcher.dispatchDelete(entity.getValue(), jsonNode);
            }
        }
    }

}
