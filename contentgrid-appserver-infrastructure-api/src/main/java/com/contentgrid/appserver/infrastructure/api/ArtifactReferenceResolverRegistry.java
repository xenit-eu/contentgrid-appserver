package com.contentgrid.appserver.infrastructure.api;

import java.util.Collection;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArtifactReferenceResolverRegistry implements ArtifactReferenceResolver {

    private final Collection<ArtifactReferenceResolver> resolvers;

    @Override
    public Artifact resolve(ArtifactReference reference) {
        for (var resolver : resolvers) {
            var result = resolver.resolve(reference);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException("ArtifactReference %s could not be resolved".formatted(reference));
    }
}
