package com.contentgrid.appserver.automations.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;

public record AutomationRepresentationModelContext(Application application, LinkFactoryProvider linkFactoryProvider) {

}
