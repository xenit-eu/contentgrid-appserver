package com.contentgrid.appserver.integration.test.fixture.invoicing;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.domain.ContentApi;
import com.contentgrid.appserver.domain.ContentApi.Content;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.FileDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MultipleRelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.registry.ApplicationResolver;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoicingApi {

    // TODO: use MockMvc?
    private final DatamodelApi datamodelApi;
    private final ContentApi contentApi;

    private final ApplicationResolver applicationResolver;

    private Application getApplication() {
        return applicationResolver.resolve(ApplicationName.of("default"));
    }

    private List<EntityInstance> findAll(EntityName entityName, String filterName, String value) {
        var application = getApplication();
        var entity = application.getRequiredEntityByName(entityName);
        var pagination = new EncodedCursorPagination(null, 100, SortData.unsorted());
        return datamodelApi.findAll(application, entity, Map.of(filterName, List.of(value)), pagination, AuthorizationContext.allowAll())
                .getContent();
    }

    private Optional<EntityInstance> findByAttribute(EntityName entityName, String filterName, String value) {
        var results = findAll(entityName, filterName, value);
        if (results.size() > 1) {
            throw new IllegalStateException("Expected one result, got %s".formatted(results.size()));
        }
        return results.stream().findFirst();
    }

    public Optional<EntityInstance> findCustomerByVat(String vat) {
        return this.findByAttribute(EntityName.of("customer"), "vat", vat);
    }

    public Optional<EntityInstance> findInvoiceByNumber(String number) {
        return this.findByAttribute(EntityName.of("invoice"), "number", number);
    }

    public Optional<EntityInstance> findPromotionCampaignByPromoCode(String promoCode) {
        return this.findByAttribute(EntityName.of("promotion-campaign"), "promo_code", promoCode);
    }

    public Optional<EntityInstance> findOrderById(EntityId id) {
        var application = getApplication();
        return (Optional<EntityInstance>) datamodelApi.findById(application,
                EntityRequest.forEntity(EntityName.of("order"), id), AuthorizationContext.allowAll());
    }

    private EntityInstance create(EntityName entityName, Map<String, Object> properties)
            throws QueryEngineException, InvalidPropertyDataException {
        var application = getApplication();
        return datamodelApi.create(application, entityName, MapRequestInputData.fromMap(properties), AuthorizationContext.allowAll());
    }

    public EntityInstance createCustomer(String name, String vat)
            throws QueryEngineException, InvalidPropertyDataException {
        var properties = new HashMap<String, Object>();
        properties.put("name", name);
        properties.put("vat", vat);
        return this.create(EntityName.of("customer"), properties);
    }

    public EntityInstance createInvoice(String number, boolean draft, boolean paid, EntityId counterparty, Set<EntityId> orders)
            throws QueryEngineException, InvalidPropertyDataException {
        var properties = new HashMap<String, Object>();
        properties.put("number", number);
        properties.put("draft", draft);
        properties.put("paid", paid);
        properties.put("counterparty", convertRelation(EntityName.of("customer"), counterparty));
        properties.put("orders", convertRelation(EntityName.of("order"), orders));
        return this.create(EntityName.of("invoice"), properties);
    }

    public EntityInstance createOrder(EntityId customer) throws QueryEngineException, InvalidPropertyDataException {
        return createOrder(customer, null, null);
    }

    public EntityInstance createOrder(EntityId customer, EntityId address, Set<EntityId> promos)
            throws QueryEngineException, InvalidPropertyDataException {
        var properties = new HashMap<String, Object>();
        properties.put("customer", convertRelation(EntityName.of("customer"), customer));
        properties.put("shipping_address", convertRelation(EntityName.of("shipping-address"), address));
        properties.put("promos", convertRelation(EntityName.of("promotion-campaign"), promos));
        return this.create(EntityName.of("order"), properties);
    }

    public EntityInstance createPromotionCampaign(String promoCode, String description)
            throws QueryEngineException, InvalidPropertyDataException {
        var properties = new HashMap<String, Object>();
        properties.put("promo_code", promoCode);
        properties.put("description", description);
        return this.create(EntityName.of("promotion-campaign"), properties);
    }

    public EntityInstance createRefund() throws QueryEngineException, InvalidPropertyDataException {
        return this.create(EntityName.of("refund"), Map.of());
    }

    public EntityInstance createShippingAddress() throws QueryEngineException, InvalidPropertyDataException {
        return this.createShippingAddress(null, null, null);
    }

    public EntityInstance createShippingAddress(String street, String zip, String city)
            throws QueryEngineException, InvalidPropertyDataException {
        var properties = new HashMap<String, Object>();
        properties.put("street", street);
        properties.put("zip", zip);
        properties.put("city", city);
        return this.create(EntityName.of("shipping-address"), properties);
    }

    public EntityInstance createShippingLabel(String from, String to) throws QueryEngineException, InvalidPropertyDataException {
        var properties = new HashMap<String, Object>();
        properties.put("from", from);
        properties.put("to", to);
        return this.create(EntityName.of("shipping-label"), properties);
    }

    private DataEntry convertRelation(EntityName targetEntity, EntityId targetId) {
        if (targetId == null) {
            return MissingDataEntry.INSTANCE;
        }
        return new RelationDataEntry(targetEntity, targetId);
    }

    private DataEntry convertRelation(EntityName targetEntity, Collection<EntityId> targetIds) {
        if (targetIds == null) {
            return MissingDataEntry.INSTANCE;
        }
        return new MultipleRelationDataEntry(targetEntity, List.copyOf(targetIds));
    }

    private Optional<EntityInstance> findTarget(EntityName entityName, EntityId entityId, RelationName relationName) {
        var application = getApplication();
        var relation = application.getRequiredRelationForEntity(entityName, relationName);
        var targetEntity = relation.getTargetEndPoint().getEntity();
        return datamodelApi.findRelationTarget(application, relation, entityId, AuthorizationContext.allowAll())
                .flatMap(targetId -> datamodelApi.findById(
                        application, EntityRequest.forEntity(targetEntity, targetId), AuthorizationContext.allowAll()
                ));
    }

    public Optional<EntityInstance> findInvoiceCounterparty(EntityId invoiceId) {
        return findTarget(EntityName.of("invoice"), invoiceId, RelationName.of("counterparty"));
    }

    public List<EntityInstance> findInvoiceOrders(EntityId invoiceId) {
        return findAll(EntityName.of("order"), "invoice._id", invoiceId.toString());
    }

    public Optional<EntityInstance> findOrderCustomer(EntityId orderId) {
        return findTarget(EntityName.of("order"), orderId, RelationName.of("customer"));
    }

    public List<EntityInstance> findOrderPromos(EntityId orderId) {
        return findAll(EntityName.of("promotion-campaign"), "orders", orderId.toString());
    }

    public Optional<EntityInstance> findOrderShippingAddress(EntityId orderId) {
        return findTarget(EntityName.of("order"), orderId, RelationName.of("shipping_address"));
    }

    public void setShippingLabelParent(EntityId shippingLabelId, EntityId parentId) {
        var application = getApplication();
        datamodelApi.setRelation(application, RelationRequest.forRelation(
                EntityName.of("shipping-label"), shippingLabelId, RelationName.of("parent")
                ), parentId, AuthorizationContext.allowAll());
    }

    public void deleteAll() {
        deleteAll(EntityName.of("refund"));
        deleteAll(EntityName.of("order"));
        deleteAll(EntityName.of("invoice"));
        deleteAll(EntityName.of("shipping-address"));
        deleteAll(EntityName.of("customer"));
        deleteAll(EntityName.of("promotion-campaign"));
        deleteAll(EntityName.of("shipping-label"));
    }

    private void deleteAll(EntityName entityName) {
        var application = getApplication();
        var entity = application.getRequiredEntityByName(entityName);
        var pagination = new EncodedCursorPagination(null, 100, SortData.unsorted());
        var items = datamodelApi.findAll(application, entity, Map.of(), pagination, AuthorizationContext.allowAll());
        for (var item : items.getContent()) {
            var entityId = item.getIdentity().getEntityId();
            datamodelApi.deleteEntity(application, EntityRequest.forEntity(entityName, entityId), AuthorizationContext.allowAll());
        }
    }

    private void storeContent(EntityName entityName, EntityId id, AttributeName attributeName, String filename, String mimetype, InputStream inputStream)
            throws InvalidPropertyDataException {
        var application = getApplication();
        var file = new FileDataEntry(filename, mimetype, () -> inputStream);
        contentApi.update(application, entityName, id, attributeName, Version.unspecified(), file, AuthorizationContext.allowAll());
    }

    private Optional<Content> findContent(EntityName entityName, EntityId id, AttributeName attributeName) {
        var application = getApplication();
        return contentApi.find(application, entityName, id, attributeName, AuthorizationContext.allowAll());
    }

    public void storeCustomerContent(EntityId id, String filename, String mimetype, InputStream inputStream)
            throws InvalidPropertyDataException {
        this.storeContent(EntityName.of("customer"), id, AttributeName.of("content"), filename, mimetype, inputStream);
    }

    public Optional<Content> findCustomerContent(EntityId id) {
        return this.findContent(EntityName.of("customer"), id, AttributeName.of("content"));
    }

    public void storeInvoiceContent(EntityId id, String filename, String mimetype, InputStream inputStream)
            throws InvalidPropertyDataException {
        this.storeContent(EntityName.of("invoice"), id, AttributeName.of("content"), filename, mimetype, inputStream);
    }

    public Optional<Content> findInvoiceContent(EntityId id) {
        return this.findContent(EntityName.of("invoice"), id, AttributeName.of("content"));
    }

    public void storeInvoiceAttachment(EntityId id, String filename, String mimetype, InputStream inputStream)
            throws InvalidPropertyDataException {
        this.storeContent(EntityName.of("invoice"), id, AttributeName.of("attachment"), filename, mimetype, inputStream);
    }

    public Optional<Content> findInvoiceAttachment(EntityId id) {
        return this.findContent(EntityName.of("invoice"), id, AttributeName.of("attachment"));
    }

    public void storeShippingLabelBarcodePicture(EntityId id, String filename, String mimetype, InputStream inputStream)
            throws InvalidPropertyDataException {
        this.storeContent(EntityName.of("shipping-label"), id, AttributeName.of("barcode_picture"), filename, mimetype, inputStream);
    }

    public Optional<Content> findShippingLabelBarcodePicture(EntityId id) {
        return this.findContent(EntityName.of("shipping-label"), id, AttributeName.of("barcode_picture"));
    }

    public void storeShippingLabelPackage(EntityId id, String filename, String mimetype, InputStream inputStream)
            throws InvalidPropertyDataException {
        this.storeContent(EntityName.of("shipping-label"), id, AttributeName.of("package"), filename, mimetype, inputStream);
    }

    public Optional<Content> findShippingLabelPackage(EntityId id) {
        return this.findContent(EntityName.of("shipping-label"), id, AttributeName.of("package"));
    }
}
