package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;

public class TestApplication {

    public static final SimpleAttribute PERSON_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    public static final SimpleAttribute PERSON_VAT = SimpleAttribute.builder()
            .name(AttributeName.of("vat"))
            .column(ColumnName.of("vat"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .constraint(Constraint.unique())
            .build();

    public static final Entity PERSON = Entity.builder()
            .name(EntityName.of("person"))
            .table(TableName.of("person"))
            .pathSegment(PathSegmentName.of("persons"))
            .linkName(LinkName.of("persons"))
            .attribute(PERSON_NAME)
            .attribute(PERSON_VAT)
            .searchFilter(ExactSearchFilter.builder()
                    .attribute(PERSON_VAT)
                    .name(FilterName.of("vat"))
                    .build())
            .searchFilter(PrefixSearchFilter.builder()
                    .attribute(PERSON_NAME)
                    .name(FilterName.of("name~prefix"))
                    .build())
            .sortableField(SortableField.builder()
                    .name(SortableName.of("vat"))
                    .propertyPath(PropertyPath.of(AttributeName.of("vat")))
                    .build())
            .sortableField(SortableField.builder()
                    .name(SortableName.of("name"))
                    .propertyPath(PropertyPath.of(AttributeName.of("name")))
                    .build())
            .build();

    public static final SimpleAttribute INVOICE_NUMBER = SimpleAttribute.builder()
            .name(AttributeName.of("number"))
            .column(ColumnName.of("number"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .constraint(Constraint.unique())
            .build();

    public static final SimpleAttribute INVOICE_AMOUNT = SimpleAttribute.builder()
            .name(AttributeName.of("amount"))
            .column(ColumnName.of("amount"))
            .type(Type.DOUBLE)
            .constraint(Constraint.required())
            .build();

    public static final SimpleAttribute INVOICE_RECEIVED = SimpleAttribute.builder()
            .name(AttributeName.of("received"))
            .column(ColumnName.of("received"))
            .type(Type.DATETIME)
            .build();

    public static final SimpleAttribute INVOICE_PAY_BEFORE = SimpleAttribute.builder()
            .name(AttributeName.of("pay_before"))
            .column(ColumnName.of("pay_before"))
            .type(Type.DATETIME)
            .build();

    public static final SimpleAttribute INVOICE_IS_PAID = SimpleAttribute.builder()
            .name(AttributeName.of("is_paid"))
            .column(ColumnName.of("is_paid"))
            .type(Type.BOOLEAN)
            .build();

    public static final ContentAttribute INVOICE_CONTENT = ContentAttribute.builder()
            .name(AttributeName.of("content"))
            .pathSegment(PathSegmentName.of("content"))
            .linkName(LinkName.of("content"))
            .idColumn(ColumnName.of("content__id"))
            .filenameColumn(ColumnName.of("content__filename"))
            .mimetypeColumn(ColumnName.of("content__mimetype"))
            .lengthColumn(ColumnName.of("content__length"))
            .build();

    public static final CompositeAttribute INVOICE_AUDIT_METADATA = CompositeAttributeImpl.builder()
            .name(AttributeName.of("audit_metadata"))
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("created_date"))
                    .column(ColumnName.of("audit_metadata__created_date"))
                    .type(Type.DATETIME)
                    .flag(CreatedDateFlag.builder().build())
                    .build())
            .attribute(UserAttribute.builder()
                    .name(AttributeName.of("created_by"))
                    .idColumn(ColumnName.of("audit_metadata__created_by_id"))
                    .namespaceColumn(ColumnName.of("audit_metadata__created_by_ns"))
                    .usernameColumn(ColumnName.of("audit_metadata__created_by_name"))
                    .flag(CreatorFlag.builder().build())
                    .build())
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("last_modified_date"))
                    .column(ColumnName.of("audit_metadata__last_modified_date"))
                    .type(Type.DATETIME)
                    .flag(ModifiedDateFlag.builder().build())
                    .build())
            .attribute(UserAttribute.builder()
                    .name(AttributeName.of("last_modified_by"))
                    .idColumn(ColumnName.of("audit_metadata__last_modified_by_id"))
                    .namespaceColumn(ColumnName.of("audit_metadata__last_modified_by_ns"))
                    .usernameColumn(ColumnName.of("audit_metadata__last_modified_by_name"))
                    .flag(ModifierFlag.builder().build())
                    .build())
            .build();

    public static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .linkName(LinkName.of("invoices"))
            .attribute(INVOICE_NUMBER)
            .attribute(INVOICE_AMOUNT)
            .attribute(INVOICE_RECEIVED)
            .attribute(INVOICE_PAY_BEFORE)
            .attribute(INVOICE_IS_PAID)
            .attribute(INVOICE_CONTENT)
            .attribute(INVOICE_AUDIT_METADATA)
            .searchFilter(ExactSearchFilter.builder()
                    .name(FilterName.of("number"))
                    .attribute(INVOICE_NUMBER)
                    .build())
            .sortableField(SortableField.builder()
                    .name(SortableName.of("number"))
                    .propertyPath(PropertyPath.of(AttributeName.of("number")))
                    .build())
            .build();

    public static final SimpleAttribute PRODUCT_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    public static final SimpleAttribute PRODUCT_DESCRIPTION = SimpleAttribute.builder()
            .name(AttributeName.of("description"))
            .column(ColumnName.of("description"))
            .type(Type.TEXT)
            .build();

    public static final SimpleAttribute PRODUCT_PRICE = SimpleAttribute.builder()
            .name(AttributeName.of("price"))
            .description("Product price")
            .column(ColumnName.of("price"))
            .type(Type.DOUBLE)
            .constraint(Constraint.required())
            .build();

    public static final SimpleAttribute PRODUCT_RELEASE_DATE = SimpleAttribute.builder()
            .name(AttributeName.of("release_date"))
            .description("Product release date")
            .column(ColumnName.of("release_date"))
            .type(Type.DATETIME)
            .build();

    public static final SimpleAttribute PRODUCT_IN_STOCK = SimpleAttribute.builder()
            .name(AttributeName.of("in_stock"))
            .description("Is product in stock")
            .column(ColumnName.of("in_stock"))
            .type(Type.BOOLEAN)
            .build();

    public static final ContentAttribute PRODUCT_PICTURE = ContentAttribute.builder()
            .name(AttributeName.of("picture"))
            .pathSegment(PathSegmentName.of("picture"))
            .linkName(LinkName.of("picture"))
            .idColumn(ColumnName.of("picture__id"))
            .filenameColumn(ColumnName.of("picture__filename"))
            .mimetypeColumn(ColumnName.of("picture__mimetype"))
            .lengthColumn(ColumnName.of("picture__length"))
            .build();

    public static final Entity PRODUCT = Entity.builder()
            .name(EntityName.of("product"))
            .table(TableName.of("product"))
            .pathSegment(PathSegmentName.of("products"))
            .linkName(LinkName.of("products"))
            .attribute(PRODUCT_NAME)
            .attribute(PRODUCT_DESCRIPTION)
            .attribute(PRODUCT_PRICE)
            .attribute(PRODUCT_RELEASE_DATE)
            .attribute(PRODUCT_IN_STOCK)
            .attribute(PRODUCT_PICTURE)
            .searchFilter(ExactSearchFilter.builder()
                    .name(FilterName.of("code"))
                    .attribute(PRODUCT_NAME)
                    .build())
            .sortableField(SortableField.builder()
                    .name(SortableName.of("name"))
                    .propertyPath(PropertyPath.of(AttributeName.of("name")))
                    .build())
            .sortableField(SortableField.builder()
                    .name(SortableName.of("price"))
                    .propertyPath(PropertyPath.of(AttributeName.of("price")))
                    .build())
            .build();

    public static final ManyToOneRelation INVOICE_CUSTOMER = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("customer"))
                    .pathSegment(PathSegmentName.of("customer"))
                    .linkName(LinkName.of("customer"))
                    .required(true)
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PERSON)
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .linkName(LinkName.of("invoices"))
                    .build())
            .targetReference(ColumnName.of("customer"))
            .build();

    public static final OneToManyRelation PERSON_INVOICES = INVOICE_CUSTOMER.inverse();

    public static final ManyToManyRelation PERSON_FRIENDS = ManyToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(PERSON)
                    .name(RelationName.of("friends"))
                    .pathSegment(PathSegmentName.of("friends"))
                    .linkName(LinkName.of("friends"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PERSON)
                    .build())
            .joinTable(TableName.of("person__friends"))
            .sourceReference(ColumnName.of("person_src_id"))
            .targetReference(ColumnName.of("person_tgt_id"))
            .build();

    public static final OneToOneRelation INVOICE_PREVIOUS = SourceOneToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("previous_invoice"))
                    .pathSegment(PathSegmentName.of("previous-invoice"))
                    .linkName(LinkName.of("previous_invoice"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("next_invoice"))
                    .pathSegment(PathSegmentName.of("next-invoice"))
                    .linkName(LinkName.of("next_invoice"))
                    .build())
            .targetReference(ColumnName.of("previous_invoice"))
            .build();

    public static final ManyToManyRelation INVOICE_PRODUCTS = ManyToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("products"))
                    .pathSegment(PathSegmentName.of("products"))
                    .linkName(LinkName.of("products"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PRODUCT)
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .linkName(LinkName.of("invoices"))
                    .build())
            .joinTable(TableName.of("invoice__products"))
            .sourceReference(ColumnName.of("invoice_id"))
            .targetReference(ColumnName.of("product_id"))
            .build();

    public static final ManyToManyRelation PRODUCT_INVOICES = INVOICE_PRODUCTS.inverse();

    public static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("demo-application"))
            .entity(INVOICE)
            .entity(PERSON)
            .entity(PRODUCT)
            .relation(INVOICE_CUSTOMER)
            .relation(INVOICE_PREVIOUS)
            .relation(PERSON_FRIENDS)
            .relation(INVOICE_PRODUCTS)
            .build();
}
