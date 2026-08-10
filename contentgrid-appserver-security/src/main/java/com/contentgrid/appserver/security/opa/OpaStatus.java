package com.contentgrid.appserver.security.opa;

import org.springframework.boot.health.contributor.Health;

public interface OpaStatus {

    Health getHealth();
}
