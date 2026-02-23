package com.contentgrid.appserver.content.lifecycle;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "contentgrid.content.lifecycle")
public class ContentLifecycleProperties {
    private boolean enabled = true;
    
    private Deletion deletion = new Deletion();
    
    @Data
    public static class Deletion {
        private boolean enabled = true;
        private Duration gracePeriod = Duration.ofDays(7);
        private int batchSize = 100;
    }
}
