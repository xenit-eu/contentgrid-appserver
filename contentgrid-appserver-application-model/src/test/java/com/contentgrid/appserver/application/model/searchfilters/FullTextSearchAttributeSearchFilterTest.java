package com.contentgrid.appserver.application.model.searchfilters;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FullTextSearchAttributeSearchFilterTest {

    @Test
    void getLocale() {
        Locale filterSpecificLocale = Locale.ENGLISH;
        Application application = mock(Application.class);
        when(application.getLocale()).thenReturn(Locale.GERMAN); // jawohl!
        Supplier<FullTextSearchAttributeSearchFilter.FullTextSearchAttributeSearchFilterBuilder> baseFilterBuilder = () -> FullTextSearchAttributeSearchFilter.builder()
                .name(FilterName.of("test-filter"))
                .attributePath(PropertyPath.of(AttributeName.of("test-attribute")));

        FullTextSearchAttributeSearchFilter filterWithoutLocale = baseFilterBuilder.get().build();
        assert filterWithoutLocale.getLocale(application).equals(Locale.GERMAN);

        FullTextSearchAttributeSearchFilter filterWithLocale = baseFilterBuilder.get()
                .locale(filterSpecificLocale)
                .build();
        assert filterWithLocale.getLocale(application).equals(filterSpecificLocale);
    }

}