package com.contentgrid.appserver.integration.test.fixture.invoicing;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.domain.authorization.AuthorizationContext;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MultipleRelationDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.RelationDataEntry;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.paging.cursor.EncodedCursorPagination;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.query.engine.api.data.SortData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.registry.ApplicationResolver;
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

    private final ApplicationResolver applicationResolver;

    private Application getApplication() {
        return applicationResolver.resolve(ApplicationName.of("default"));
    }

    private Optional<EntityInstance> findByAttribute(EntityName entityName, String filterName, String value) {
        var application = getApplication();
        var entity = application.getRequiredEntityByName(entityName);
        var pagination = new EncodedCursorPagination(null, 1, SortData.unsorted());
        return datamodelApi.findAll(application, entity, Map.of(filterName, List.of(value)), pagination, AuthorizationContext.allowAll())
                .getContent().stream().findFirst();
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
}
