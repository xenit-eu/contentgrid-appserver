package com.contentgrid.appserver.rest.hal.links;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.springframework.hateoas.Link;

@EqualsAndHashCode(callSuper = true)
public class EtagLink extends Link {

    @Delegate
    @EqualsAndHashCode.Exclude
    private final Link delegate;

    @Getter
    private final String etag;

    public EtagLink(@NonNull Link delegate, @NonNull String etag) {
        super(delegate.getTemplate(), delegate.getRel());
        this.delegate = delegate;
        this.etag = etag;
    }
}
