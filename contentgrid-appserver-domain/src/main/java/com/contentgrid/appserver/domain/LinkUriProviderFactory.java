package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;

public interface LinkUriProviderFactory {
    LinkUriProvider createLinkUriProvider(Application application);
}
