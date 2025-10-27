package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.Application;
import lombok.NonNull;

import java.util.Locale;

public interface LocaleAwareSearchFilter {

    default Locale getLocale(@NonNull Application application) {
        return application.getLocale();
    }

}
