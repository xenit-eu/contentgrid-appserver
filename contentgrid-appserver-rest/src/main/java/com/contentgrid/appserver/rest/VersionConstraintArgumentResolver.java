package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import com.contentgrid.appserver.domain.values.version.UnspecifiedVersion;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves ETags from If-Match/If-None-Match headers to {@link VersionConstraint} objects
 */
public class VersionConstraintArgumentResolver implements HandlerMethodArgumentResolver, Converter<Version, ETag> {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return VersionConstraint.class == parameter.getParameterType();
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

        var matching = createEtags(webRequest.getHeaderValues(HttpHeaders.IF_MATCH))
                .map(etag -> toVersion(etag, true)) // Strong comparison per RFC 9110 13.1.1
                .toList();

        var notMatching = createEtags(webRequest.getHeaderValues(HttpHeaders.IF_NONE_MATCH))
                .map(etag -> toVersion(etag, false)) // Weak comparison per RFC 9110 13.1.2
                .toList();

        if (matching.isEmpty()) {
            // If there is no If-Match constraint, any version is okay
            matching = List.of(VersionConstraint.ANY);
        }

        return new ConstrainedVersion(matching, notMatching);
    }

    private VersionConstraint toVersion(ETag eTag, boolean strongComparison) {
        if (eTag.isWildcard()) {
            return Version.unspecified();
        }
        if (strongComparison && eTag.weak()) {
            // Weak etag used under strong comparison; they should never match anything.
            // -> No acceptable version, all versions are forbidden
            return new ConstrainedVersion(List.of(), List.of(VersionConstraint.ANY));
        }
        // Note that under weak comparison, a strong and a weak etag with the same tag value are still a match
        return Version.exactly(eTag.tag());
    }

    private Stream<ETag> createEtags(String[] headers) {
        if (headers == null) {
            return Stream.empty();
        }
        return Arrays.stream(headers)
                .flatMap(header -> ETag.parse(header).stream());
    }

    @Override
    public ETag convert(@NonNull Version source) {
        return switch (source) {
            case UnspecifiedVersion ignored -> null;
            // For entities, *technically* speaking according to RFC 9110 (8.8.3.1), this should be a weak ETag
            // (because its based on the stored data,instead of on the exact output on the wire)
            // However, If-Match *requires* a strong ETag to function (Also according to RFC9110 13.1.1)
            // And since we don't serve partial content in response to range-requests on entity endpoints,
            // a not byte-for-byte identical response with the same ETag should not be a problem.
            case ExactlyVersion exactlyEntityVersion -> new ETag(exactlyEntityVersion.getVersion(), false);
        };
    }

    /**
     * Places a requirement on the entity version to be any of the acceptable versions, but none of the forbidden versions
     * <p>
     * This is primarily used for supplying the equivalent of HTTP If-Match/If-None-Match to the versioning
     * <p>
     * This is only used for requests to the query engine. It requires the entity to satisfy the constraints when
     * performing the operation, otherwise the request must be rejected.
     * It is never present in responses from the query engine.
     */
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public static final class ConstrainedVersion implements VersionConstraint {

        private final Iterable<VersionConstraint> acceptableVersions;
        private final Iterable<VersionConstraint> forbiddenVersions;

        @Override
        public boolean isSatisfiedBy(@NonNull Version otherVersion) {
            for (var version : forbiddenVersions) {
                if (version.isSatisfiedBy(otherVersion)) {
                    return false;
                }
            }
            for (var version : acceptableVersions) {
                if (version.isSatisfiedBy(otherVersion)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            var sb = new StringBuilder()
                    .append("is any of [");
            var hasAcceptable = false;

            for (var acceptableVersion : acceptableVersions) {
                if (hasAcceptable) {
                    sb.append(", ");
                }
                sb.append(acceptableVersion);
                hasAcceptable = true;
            }
            sb.append("]");

            var hasForbidden = false;
            for (var forbiddenVersion : forbiddenVersions) {
                if (!hasForbidden) {
                    sb.append(" and not any of [");
                } else {
                    sb.append(", ");
                }
                hasForbidden = true;
                sb.append(forbiddenVersion);
            }
            if (hasForbidden) {
                sb.append("]");
            }

            return sb.toString();
        }
    }
}
