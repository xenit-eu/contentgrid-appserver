package com.contentgrid.appserver.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentLifecyclePropertiesTest {

    @Test
    void defaultValues_areCorrect() {
        var properties = new ContentLifecycleProperties();
        
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDeletion()).isNotNull();
        assertThat(properties.getDeletion().isEnabled()).isTrue();
        assertThat(properties.getDeletion().getGracePeriod().toDays()).isEqualTo(7);
        assertThat(properties.getDeletion().getBatchSize()).isEqualTo(100);
    }
}
