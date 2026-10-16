package com.contentgrid.appserver.rest.entity.assembler;

import com.contentgrid.appserver.domain.data.EntityLinkData;
import com.contentgrid.appserver.domain.data.EntityLinkInstance;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.rest.hal.forms.RepresentationModelWithTemplates;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EntityLinkDataRepresentationModel extends RepresentationModelWithTemplates<EntityLinkDataRepresentationModel> {

    private final EntityIdentity entityIdentity;
    private final EntityLinkData entityLinkData;

    public static EntityLinkDataRepresentationModel from(EntityLinkInstance entityLinkInstance) {
        return new EntityLinkDataRepresentationModel(
                entityLinkInstance.getEntityIdentity(),
                entityLinkInstance.getEntityLinkData()
        );
    }

}
