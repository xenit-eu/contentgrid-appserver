package com.contentgrid.appserver.rest.paging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jackson mixin to optionally apply to {@link ItemCountPageMetadata} to omit legacy pagination properties
 */
@JsonIgnoreProperties({"number", "totalElements", "totalPages"})
public interface ItemCountPageMetadataOmitLegacyPropertiesMixin {

}
