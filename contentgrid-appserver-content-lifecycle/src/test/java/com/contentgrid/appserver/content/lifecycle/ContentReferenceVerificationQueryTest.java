package com.contentgrid.appserver.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jooq.DSLContext;

class ContentReferenceVerificationQueryTest {

    private DSLContext dslContext;
    private Supplier<Application> applicationSupplier;
    private ContentReferenceVerificationQuery query;

    @BeforeEach
    void setUp() {
        dslContext = mock(DSLContext.class);
        applicationSupplier = mock(Supplier.class);
        query = new ContentReferenceVerificationQuery(dslContext, applicationSupplier);
    }

    @Test
    void isStillReferenced_returnsFalse_whenNoEntities() {
        Application app = mock(Application.class);
        when(app.getEntities()).thenReturn(Collections.emptyList());
        when(applicationSupplier.get()).thenReturn(app);

        boolean result = query.isStillReferenced("content-123");

        assertThat(result).isFalse();
    }

    @Test
    void isStillReferenced_returnsFalse_whenNoContentAttributes() {
        Application app = mock(Application.class);
        Entity entity = mock(Entity.class);
        when(app.getEntities()).thenReturn(List.of(entity));
        when(entity.getContentAttributes()).thenReturn(Collections.emptyList());
        when(applicationSupplier.get()).thenReturn(app);

        boolean result = query.isStillReferenced("content-123");

        assertThat(result).isFalse();
    }
}
