package com.contentgrid.appserver.domain.content;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.contentstore.api.ContentStore;

public interface ContentStoreResolver {

    ContentStore resolve(Application application);

}
