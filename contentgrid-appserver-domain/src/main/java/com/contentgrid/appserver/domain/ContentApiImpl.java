package com.contentgrid.appserver.domain;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.content.ContentStoreResolver;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.version.VersionConstraint;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class ContentApiImpl implements ContentApi {
    private final DatamodelApiImpl datamodelApi;
    private final ContentStoreResolver contentStoreResolver;

    private AttributeDataContent extractContent(
            @NonNull Application application,
            @NonNull InternalEntityInstance entityData,
            @NonNull AttributeName attributeName
    ) {
        var contentStore = contentStoreResolver.resolve(application);
        var contentAttribute = application.getRequiredEntityByName(entityData.getIdentity().getEntityName())
                .getAttributeByName(attributeName)
                .filter(ContentAttribute.class::isInstance)
                .map(ContentAttribute.class::cast)
                .orElseThrow(); // TODO: throw a properly typed exception when the wrong attribute name is given

        return new AttributeDataContent(
                contentStore, contentAttribute,
                entityData.getByAttributeName(attributeName, CompositeAttributeData.class).orElse(null)
        );
    }

    @Override
    public Optional<Content> find(@NonNull Application application, @NonNull EntityName entityName,
            @NonNull EntityId id, @NonNull AttributeName attributeName,
            @NonNull AuthorizationContext authorizationContext) {

        var entityData = datamodelApi.findById(application, EntityRequest.forEntity(entityName, id), authorizationContext)
                .orElseThrow(() -> new EntityIdNotFoundException(entityName, id));
        return Optional.of(extractContent(application, entityData, attributeName))
                .filter(content -> content.getContentId().isPresent())
                .map(Content.class::cast);
    }

    @Override
    public Content update(@NonNull Application application, @NonNull EntityName entityName, @NonNull EntityId id,
            @NonNull AttributeName attributeName, @NonNull VersionConstraint versionConstraint,
            @NonNull DataEntry.FileDataEntry file, @NonNull AuthorizationContext authorizationContext
    ) throws InvalidPropertyDataException {
        var original = requireEntityWithConstraint(application, entityName, id, attributeName, versionConstraint,
                authorizationContext);

        var updated = datamodelApi.updatePartial(application, original, MapRequestInputData.fromMap(Map.of(
                attributeName.getValue(), file
        )), authorizationContext);

        return extractContent(application, updated, attributeName);
    }

    private InternalEntityInstance requireEntityWithConstraint(
            Application application,
            EntityName entityName,
            EntityId id,
            AttributeName attributeName,
            VersionConstraint versionConstraint,
            @NonNull AuthorizationContext authorizationContext) {
        var original = datamodelApi.findById(application, EntityRequest.forEntity(entityName, id), authorizationContext)
                .orElseThrow(() -> new EntityIdNotFoundException(
                        entityName, id));

        var contentVersion = extractContent(application, original, attributeName).getVersion();

        if(!versionConstraint.isSatisfiedBy(contentVersion)) {
            throw new UnsatisfiedVersionException(
                    contentVersion,
                    versionConstraint
            );
        }
        return original;
    }

    @Override
    public void delete(@NonNull Application application, @NonNull EntityName entityName, @NonNull EntityId id,
            @NonNull AttributeName attributeName, @NonNull VersionConstraint versionConstraint,
            @NonNull AuthorizationContext authorizationContext) throws InvalidPropertyDataException {
        var original = requireEntityWithConstraint(application, entityName, id, attributeName, versionConstraint,
                authorizationContext);
        datamodelApi.updatePartial(application, original, MapRequestInputData.fromMap(Map.of(
                attributeName.getValue(), NullDataEntry.INSTANCE
        )), authorizationContext);
    }

    // package-private for testing
    @SneakyThrows(NoSuchAlgorithmException.class)
    static String hash(String... inputs) {
        var md = MessageDigest.getInstance("SHA256");
        for (var input : inputs) {
            md.update(input.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0); // NUL-byte as separator for fields
        }
        var digest = md.digest();
        // An always-positive bigint, limited to 16 bytes (truncated sha-256 hash), to reduce the size of the version
        // This reduces the size of the version from 50 characters to a more sensible 25 characters
        return new BigInteger(1, digest, 0, 16).toString(Character.MAX_RADIX);
    }
}
