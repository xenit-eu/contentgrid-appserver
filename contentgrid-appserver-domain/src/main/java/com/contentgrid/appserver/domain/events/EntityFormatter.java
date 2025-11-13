package com.contentgrid.appserver.domain.events;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.fasterxml.jackson.core.TreeNode;

public interface EntityFormatter {
    TreeNode format(Application application, EntityInstance entityInstance);
}
