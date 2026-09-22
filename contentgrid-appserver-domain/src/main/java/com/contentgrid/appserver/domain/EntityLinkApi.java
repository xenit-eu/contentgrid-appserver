package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.values.LinkRequest;
import java.util.Optional;
import lombok.NonNull;

public interface EntityLinkApi {

    Optional<StoredLinkValue> findLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    );

    StoredLinkValue updateLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull DataEntry value,
            @NonNull AuthorizationContext authorizationContext
    );

    void deleteLink(
            @NonNull Application application,
            @NonNull LinkRequest linkRequest,
            @NonNull AuthorizationContext authorizationContext
    );
}
