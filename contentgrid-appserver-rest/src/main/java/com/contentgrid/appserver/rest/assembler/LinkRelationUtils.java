package com.contentgrid.appserver.rest.assembler;

import com.contentgrid.appserver.application.model.values.LinkRel;
import lombok.experimental.UtilityClass;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;

@UtilityClass
public class LinkRelationUtils {

    public static LinkRelation from(LinkRel linkRel) {
        if (linkRel.isCuried()) {
            return HalLinkRelation.curied(linkRel.getCurie(), linkRel.getRel());
        } else {
            return HalLinkRelation.uncuried(linkRel.getRel());
        }
    }

}
