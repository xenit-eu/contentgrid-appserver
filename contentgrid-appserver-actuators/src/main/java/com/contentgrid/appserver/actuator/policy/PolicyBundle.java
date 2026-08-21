package com.contentgrid.appserver.actuator.policy;

/**
 * An OPA bundle together with the entity tag that identifies its contents.
 *
 * @param content the gzipped tarball
 * @param etag a hash over {@code content}, used to answer conditional requests
 */
public record PolicyBundle(byte[] content, String etag) {

}
