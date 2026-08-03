CREATE SCHEMA "V1";
CREATE VIEW "V1"."_dek_storage" AS 
	SELECT "content_id", "kek_label", "algorithm", "encrypted_dek", "iv" FROM "_dek_storage";
CREATE VIEW "V1"."person" AS 
	SELECT "id", "_version", "first_name", "last_name", "comment", "birth_date" FROM "person";
CREATE VIEW "V1"."shipment" AS 
	SELECT "id", "_version", "shipped_timestamp", "address_city", "address_country", "address_residence_street", "address_residence_number", "invoice" FROM "shipment";
CREATE VIEW "V1"."invoice" AS 
	SELECT "id", "_version", "number", "amount", "content__id", "content__length", "content__mimetype", "content__filename", "customer" FROM "invoice";
CREATE VIEW "V1"."person__friends" AS 
	SELECT "person_src_id", "person_tgt_id" FROM "person__friends";
