package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.links.LinkIdentity;
import com.contentgrid.appserver.application.model.links.StoredEntityLink;
import com.contentgrid.appserver.application.model.propertypath.AttributePath;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.LinkRequest;
import java.util.Optional;
import lombok.NonNull;

// TODO: merge into DatamodelApi
public interface EntityLinkApi {

    Optional<StoredLinkValue> findLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    );

    /**
     * @return An object with data as stored and metadata
     */
    StoredLinkValue updateLink(
            @NonNull Application application,
            @NonNull EntityRequest entityRequest,
            @NonNull StoredEntityLink link,
            @NonNull DataEntry value,
            @NonNull AuthorizationContext authorizationContext
    ) throws InvalidPropertyDataException;

    /**
     * Clears the data stored for a link.
     *
     * @param application the application context
     * @param entityRequest the identity of the entity holding the link
     * @param link the identity of the link to clear
     */
    void deleteLink(
            @NonNull Application application,
            @NonNull EntityRequest entityRequest,
            @NonNull LinkIdentity link,
            @NonNull AuthorizationContext authorizationContext
    ) throws InvalidPropertyDataException;

}
