-- BEGIN: enable-feature scribe/many-to-many-join-table-drop-additional-id-column
-- END: enable-feature scribe/many-to-many-join-table-drop-additional-id-column
-- BEGIN: enable-feature scribe/namespaced-content-property-columns
-- END: enable-feature scribe/namespaced-content-property-columns
-- BEGIN: enable-feature app/embedded-content-property
-- END: enable-feature app/embedded-content-property
-- BEGIN: enable-feature app/default-curied-link-relations
-- END: enable-feature app/default-curied-link-relations
-- BEGIN: enable-feature scribe/hash-long-database-index-names
-- END: enable-feature scribe/hash-long-database-index-names
-- BEGIN: enable-feature app/optimistic-locking
-- END: enable-feature app/optimistic-locking
-- BEGIN: enable-feature scribe/omit-noop-compatibility-views
-- END: enable-feature scribe/omit-noop-compatibility-views
-- BEGIN: enable-feature scribe/index-foreign-key-columns
-- END: enable-feature scribe/index-foreign-key-columns
-- BEGIN: enable-feature scribe/utf8-normalize-text-indexes
-- END: enable-feature scribe/utf8-normalize-text-indexes
-- BEGIN: enable-feature app/cursor-based-pagination
-- END: enable-feature app/cursor-based-pagination
-- BEGIN: enable-feature app/omit-legacy-page-metadata
-- END: enable-feature app/omit-legacy-page-metadata
-- BEGIN: enable-feature app/content-encryption
CREATE TABLE "_dek_storage" ("content_id" text NOT NULL, "kek_label" text NOT NULL, "algorithm" text NOT NULL, "encrypted_dek" bytea NOT NULL, "iv" bytea NOT NULL, PRIMARY KEY ("content_id", "kek_label"));
-- END: enable-feature app/content-encryption
-- BEGIN: enable-feature scribe/index-policy-conditions
-- END: enable-feature scribe/index-policy-conditions
-- BEGIN: enable-feature app/convert-audit-metadata
-- END: enable-feature app/convert-audit-metadata
CREATE TABLE "person" ("id" uuid NOT NULL, "_version" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id"));
ALTER TABLE "person" ADD COLUMN "created_at" timestamptz NULL;
-- BEGIN: Create user property person.created_by
ALTER TABLE "person" ADD COLUMN "created_by__id" text NULL;
ALTER TABLE "person" ADD COLUMN "created_by__ns" text NULL;
ALTER TABLE "person" ADD COLUMN "created_by__name" text NULL;
-- END: Create user property person.created_by
ALTER TABLE "person" ADD COLUMN "last_modified_at" timestamptz NULL;
-- BEGIN: Create user property person.last_modified_by
ALTER TABLE "person" ADD COLUMN "last_modified_by__id" text NULL;
ALTER TABLE "person" ADD COLUMN "last_modified_by__ns" text NULL;
ALTER TABLE "person" ADD COLUMN "last_modified_by__name" text NULL;
-- END: Create user property person.last_modified_by
ALTER TABLE "person" DROP COLUMN "created_at";
-- BEGIN: Delete user property person.created_by
ALTER TABLE "person" DROP COLUMN "created_by__id";
ALTER TABLE "person" DROP COLUMN "created_by__ns";
ALTER TABLE "person" DROP COLUMN "created_by__name";
-- END: Delete user property person.created_by
ALTER TABLE "person" DROP COLUMN "last_modified_at";
-- BEGIN: Delete user property person.last_modified_by
ALTER TABLE "person" DROP COLUMN "last_modified_by__id";
ALTER TABLE "person" DROP COLUMN "last_modified_by__ns";
ALTER TABLE "person" DROP COLUMN "last_modified_by__name";
-- END: Delete user property person.last_modified_by
CREATE TABLE "shipment" ("id" uuid NOT NULL, "_version" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id"));
ALTER TABLE "shipment" ADD COLUMN "created_at" timestamptz NULL;
-- BEGIN: Create user property shipment.created_by
ALTER TABLE "shipment" ADD COLUMN "created_by__id" text NULL;
ALTER TABLE "shipment" ADD COLUMN "created_by__ns" text NULL;
ALTER TABLE "shipment" ADD COLUMN "created_by__name" text NULL;
-- END: Create user property shipment.created_by
ALTER TABLE "shipment" ADD COLUMN "last_modified_at" timestamptz NULL;
-- BEGIN: Create user property shipment.last_modified_by
ALTER TABLE "shipment" ADD COLUMN "last_modified_by__id" text NULL;
ALTER TABLE "shipment" ADD COLUMN "last_modified_by__ns" text NULL;
ALTER TABLE "shipment" ADD COLUMN "last_modified_by__name" text NULL;
-- END: Create user property shipment.last_modified_by
ALTER TABLE "shipment" DROP COLUMN "created_at";
-- BEGIN: Delete user property shipment.created_by
ALTER TABLE "shipment" DROP COLUMN "created_by__id";
ALTER TABLE "shipment" DROP COLUMN "created_by__ns";
ALTER TABLE "shipment" DROP COLUMN "created_by__name";
-- END: Delete user property shipment.created_by
ALTER TABLE "shipment" DROP COLUMN "last_modified_at";
-- BEGIN: Delete user property shipment.last_modified_by
ALTER TABLE "shipment" DROP COLUMN "last_modified_by__id";
ALTER TABLE "shipment" DROP COLUMN "last_modified_by__ns";
ALTER TABLE "shipment" DROP COLUMN "last_modified_by__name";
-- END: Delete user property shipment.last_modified_by
CREATE TABLE "invoice" ("id" uuid NOT NULL, "_version" bigint NOT NULL DEFAULT 0, PRIMARY KEY ("id"));
ALTER TABLE "invoice" ADD COLUMN "created_at" timestamptz NULL;
-- BEGIN: Create user property invoice.created_by
ALTER TABLE "invoice" ADD COLUMN "created_by__id" text NULL;
ALTER TABLE "invoice" ADD COLUMN "created_by__ns" text NULL;
ALTER TABLE "invoice" ADD COLUMN "created_by__name" text NULL;
-- END: Create user property invoice.created_by
ALTER TABLE "invoice" ADD COLUMN "last_modified_at" timestamptz NULL;
-- BEGIN: Create user property invoice.last_modified_by
ALTER TABLE "invoice" ADD COLUMN "last_modified_by__id" text NULL;
ALTER TABLE "invoice" ADD COLUMN "last_modified_by__ns" text NULL;
ALTER TABLE "invoice" ADD COLUMN "last_modified_by__name" text NULL;
-- END: Create user property invoice.last_modified_by
ALTER TABLE "invoice" DROP COLUMN "created_at";
-- BEGIN: Delete user property invoice.created_by
ALTER TABLE "invoice" DROP COLUMN "created_by__id";
ALTER TABLE "invoice" DROP COLUMN "created_by__ns";
ALTER TABLE "invoice" DROP COLUMN "created_by__name";
-- END: Delete user property invoice.created_by
ALTER TABLE "invoice" DROP COLUMN "last_modified_at";
-- BEGIN: Delete user property invoice.last_modified_by
ALTER TABLE "invoice" DROP COLUMN "last_modified_by__id";
ALTER TABLE "invoice" DROP COLUMN "last_modified_by__ns";
ALTER TABLE "invoice" DROP COLUMN "last_modified_by__name";
-- END: Delete user property invoice.last_modified_by
ALTER TABLE "person" ADD COLUMN "first_name" text NULL;
ALTER TABLE "person" ADD COLUMN "last_name" text NULL;
ALTER TABLE "person" ADD COLUMN "comment" text NULL;
CREATE SCHEMA "extensions";
CREATE EXTENSION unaccent SCHEMA "extensions";
CREATE FUNCTION "extensions".contentgrid_prefix_search_normalize(arg text)
	RETURNS text
	LANGUAGE sql IMMUTABLE RETURNS NULL ON NULL INPUT PARALLEL SAFE
RETURN "extensions".unaccent('extensions.unaccent', lower(normalize(arg, NFKC)));
ALTER TABLE "person" ADD COLUMN "birth_date" date NULL;
ALTER TABLE "shipment" ADD COLUMN "shipped_timestamp" timestamptz NULL;
ALTER TABLE "shipment" ADD COLUMN "address_city" text NULL;
ALTER TABLE "shipment" ADD COLUMN "address_country" text NULL;
ALTER TABLE "shipment" ADD COLUMN "address_residence_street" text NULL;
ALTER TABLE "shipment" ADD COLUMN "address_residence_number" text NULL;
ALTER TABLE "invoice" ADD COLUMN "number" text NULL;
ALTER TABLE "invoice" ALTER COLUMN "number" SET NOT NULL;
ALTER TABLE "invoice" ADD COLUMN "amount" decimal NULL;
ALTER TABLE "invoice" ALTER COLUMN "amount" SET NOT NULL;
-- BEGIN: Create of content property invoice.content
ALTER TABLE "invoice" ADD COLUMN "content__id" text NULL;
ALTER TABLE "invoice" ADD COLUMN "content__length" bigint NULL;
ALTER TABLE "invoice" ADD COLUMN "content__mimetype" text NULL;
ALTER TABLE "invoice" ADD COLUMN "content__filename" text NULL;
-- END: Create of content property invoice.content
ALTER TABLE "shipment" ADD COLUMN "invoice" uuid NULL REFERENCES "invoice"("id");
ALTER TABLE "invoice" ADD COLUMN "customer" uuid NULL REFERENCES "person"("id");
CREATE TABLE "person__friends" ("person_src_id" uuid NOT NULL REFERENCES "person"("id"), "person_tgt_id" uuid NOT NULL REFERENCES "person"("id"), PRIMARY KEY ("person_src_id", "person_tgt_id"));
