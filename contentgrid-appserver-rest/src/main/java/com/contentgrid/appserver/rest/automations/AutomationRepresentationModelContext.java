package com.contentgrid.appserver.rest.automations;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;

public record AutomationRepresentationModelContext(Application application, LinkFactoryProvider linkFactoryProvider) {

}
