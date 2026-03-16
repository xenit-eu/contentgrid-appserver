package com.contentgrid.appserver.content.lifecycle;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contentgrid.appserver.content.lifecycle")
@Getter
@Setter
public class ContentLifecycleProperties {

    private Deletion deletion = new Deletion();

    @Getter
    @Setter
    public static class Deletion {

        /**
         * Grace period after a content object is dereferenced before it becomes eligible for deletion.
         * Expressed as an ISO-8601 duration (e.g. {@code P7D} for 7 days).
         */
        private Duration gracePeriod = Duration.parse("P7D");

        /**
         * Maximum number of deletion candidates processed per job run.
         */
        private int batchSize = 100;
    }
}
