package com.contentgrid.appserver.domain.spi.blueprintartifact;

import java.util.Collection;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BlueprintArtifactReferenceResolverRegistry implements BlueprintArtifactReferenceResolver {

    private final Collection<BlueprintArtifactReferenceResolver> resolvers;

    @Override
    public BlueprintArtifact resolve(BlueprintArtifactReference reference) {
        for (var resolver : resolvers) {
            var result = resolver.resolve(reference);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException("BlueprintArtifactReference %s could not be resolved".formatted(reference));
    }
}
