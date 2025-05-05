package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Setter;

public class ApplicationRegistry {
    private Map<ApplicationName, Application> registry = new HashMap<>();

    // for tests?
    @Setter(AccessLevel.PRIVATE)
    private ApplicationName _default;

    private ApplicationRegistry() {}

    public static ApplicationRegistry bootstrap(Application... applications) {
        ApplicationRegistry appRegistry = new ApplicationRegistry();
        for (Application app : applications) {
            appRegistry.register(app);
        }
        if (applications.length > 0) {
            appRegistry.set_default(applications[0].getName());
        }
        return appRegistry;
    }

    public void register(Application application) {
        this.registry.put(application.getName(), application);
    }

    public Application getDefault() {
        return registry.get(_default);
    }

    public Application get(ApplicationName name) {
        var result = registry.get(name);
        if (result == null) {
            return getDefault();
        }
        return result;
    }

}
