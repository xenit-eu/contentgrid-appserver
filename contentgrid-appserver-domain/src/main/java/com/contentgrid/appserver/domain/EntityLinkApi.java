package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.EntityLinkData.StoredLinkData;
import com.contentgrid.appserver.domain.data.EntityLinkInstance;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.values.LinkRequest;
import com.contentgrid.appserver.domain.values.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.domain.values.exception.LinkNotFoundException;
import com.contentgrid.appserver.domain.values.exception.UnsatisfiedVersionException;
import java.net.URI;
import java.util.Optional;
import lombok.NonNull;

/**
 * Access to the stored value of {@link com.contentgrid.appserver.application.model.links.StoredEntityLink}s.
 * <p>
 * Every returned {@link EntityLinkInstance} carries a {@link StoredLinkData}.
 */
public interface EntityLinkApi {

    /**
     * Finds the stored value of a link.
     *
     * @return the link, or empty when the link has no stored value or when its version does not satisfy the
     * requested version constraint
     * @throws LinkNotFoundException when the entity has no stored link with the requested identity
     * @throws EntityIdNotFoundException when the entity does not exist
     */
    Optional<EntityLinkInstance> findLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    ) throws EntityIdNotFoundException, LinkNotFoundException;

    /**
     * Finds the stored value of a link.
     *
     * @throws LinkNotFoundException when the entity has no stored link with the requested identity, or when the
     * link has no stored value
     * @throws EntityIdNotFoundException when the entity does not exist
     * @throws UnsatisfiedVersionException when the version of the stored value does not satisfy the requested
     * version constraint
     */
    EntityLinkInstance findRequiredLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    ) throws EntityIdNotFoundException, LinkNotFoundException, UnsatisfiedVersionException;

    /**
     * Writes the stored value of a link.
     * <p>
     * The requested version constraint is checked against the current stored value; a link without stored value
     * has a {@linkplain com.contentgrid.appserver.domain.values.version.Version#nonExisting() non-existing} version.
     *
     * @return the link after the write, or empty when the written value cleared the link
     */
    Optional<EntityLinkInstance> updateLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull RequestInputData inputData,
            @NonNull AuthorizationContext authorizationContext
    ) throws InvalidPropertyDataException;

    /**
     * Clears the stored value of a link.
     */
    void deleteLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    ) throws InvalidPropertyDataException;
}
