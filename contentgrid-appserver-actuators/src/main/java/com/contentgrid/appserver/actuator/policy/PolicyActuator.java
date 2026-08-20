package com.contentgrid.appserver.actuator.policy;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import java.io.IOException;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

@WebEndpoint(id = "policy")
public class PolicyActuator {

    private final RegoPolicyLoader policyLoader;
    private final String policyPackage;

    public PolicyActuator(BlueprintArtifact blueprintArtifact, String policyPackage) {
        this.policyLoader = new RegoPolicyLoader(blueprintArtifact);
        this.policyPackage = policyPackage;
    }

    @ReadOperation(producesFrom = RegoProducible.class)
    public String readPolicy() throws IOException, BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        return policyLoader.readPolicy(policyPackage);
    }
}
