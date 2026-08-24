package com.contentgrid.appserver.actuator.policy;

import java.util.Objects;
import lombok.NonNull;

/**
 * An OPA bundle together with the entity tag that identifies its contents.
 *
 * @param content the gzipped tarball
 * @param etag a hash over {@code content}, used to answer conditional requests
 */
public record PolicyBundle(byte[] content, String etag) {

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PolicyBundle that)) {
            return false;
        }
        return Objects.equals(etag(), that.etag());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(etag());
    }

    @Override
    @NonNull
    public String toString() {
        return "PolicyBundle{" +
                "etag='" + etag + '\'' +
                '}';
    }
}
