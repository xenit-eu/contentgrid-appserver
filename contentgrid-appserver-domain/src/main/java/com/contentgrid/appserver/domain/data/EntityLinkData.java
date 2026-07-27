package com.contentgrid.appserver.domain.data;

import com.contentgrid.appserver.application.model.links.LinkIdentity;
import java.net.URI;
import lombok.Value;

@Value
public class EntityLinkData {
    LinkIdentity identity;
    URI profile;
    String href;

}
