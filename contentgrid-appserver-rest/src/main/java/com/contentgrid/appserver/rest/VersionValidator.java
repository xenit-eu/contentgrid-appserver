package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.ETag;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

@Component
@RequiredArgsConstructor
public class VersionValidator {

    @NonNull
    private final ConversionService conversionService;

    /**
     * Checks whether the requested entity's version matches the versionConstraint.
     * Returns true when it matches, false when the entity is newer. This will also
     * set the webRequest's HTTP response status to 304 and 200, respectively.
     * <p>
     * May throw an {@link UnsatisfiedVersionException} when the response is 412 Precondition Failed.
     *
     * @param webRequest The http request and response.
     * @param versionConstraint The constraint to be satisfied.
     * @param version The actual version.
     * @return whether the entity's version is unchanged
     * and response is 304 Not Modified
     * @throws UnsatisfiedVersionException If the response should be 412 Precondition failed
     */
    public boolean checkVersion(@NonNull WebRequest webRequest, @NonNull VersionConstraint versionConstraint, @NonNull Version version)
            throws UnsatisfiedVersionException {
        var eTag = calculateETag(version);
        // First check not-modified for a 304 response before performing If-Match validation (with a 412 response)
        if (webRequest.checkNotModified(eTag)) {
            return true;
        }
        if (!versionConstraint.isSatisfiedBy(version)) {
            throw new UnsatisfiedVersionException(version, versionConstraint);
        }
        return false;
    }

    public String calculateETag(@NonNull Version version) {
        return Optional.ofNullable(conversionService.convert(version, ETag.class))
                .map(ETag::formattedTag)
                .orElse(null);
    }
}
