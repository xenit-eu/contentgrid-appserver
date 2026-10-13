package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.domain.values.LinkRequest;
import com.contentgrid.appserver.domain.values.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.domain.values.exception.LinkNotFoundException;
import com.contentgrid.appserver.domain.values.exception.UnsatisfiedVersionException;
import java.util.Optional;
import lombok.NonNull;

public interface EntityLinkApi {

    Optional<StoredLinkValue> findLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    );

    StoredLinkValue findRequiredLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    ) throws EntityIdNotFoundException, LinkNotFoundException, UnsatisfiedVersionException;

    StoredLinkValue updateLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull RequestInputData inputData,
            @NonNull AuthorizationContext authorizationContext
    ) throws InvalidPropertyDataException;

    void deleteLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    ) throws InvalidPropertyDataException;
}
