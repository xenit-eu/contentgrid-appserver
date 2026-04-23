# Note: ${system.policy.package} is automatically replaced with the package name.
# Replace it when deploying this file to OPA directly.
package ${system.policy.package}

import rego.v1

util.extract_content_type(header) := content_type if {
	mime_type := trim_space(split(header, ";")[0])
	content_type := lower(mime_type)
}
default util.content_type_in(headers, accepted_content_types) := false

util.content_type_in(headers, accepted_content_types) if {
	count(headers) == 1
	extracted_mime_type := util.extract_content_type(headers[0])
	extracted_mime_type == accepted_content_types[_]
}
default util.request.content_type_in(content_types) := false

util.request.content_type_in(content_types) if {
	util.content_type_in(input.request.headers["content-type"], content_types)
}
default can_read_invoice := false

default can_create_invoice := false

default can_update_invoice := false

default can_delete_invoice := false

default can_read_person := false

default can_create_person := false

default can_update_person := false

default can_delete_person := false

default can_read_shipment := false

default can_create_shipment := false

default can_update_shipment := false

default can_delete_shipment := false

# Policy a4ikordm252q
# - input.entity is type 'invoice'
can_read_invoice if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_create_invoice if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_update_invoice if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_delete_invoice if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
# End policy a4ikordm252q
# Policy pno32qrrcasa
# - input.entity is type 'person'
can_read_person if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_create_person if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_update_person if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_delete_person if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
# End policy pno32qrrcasa
# Policy f45cinlkkxea
# - input.entity is type 'shipment'
can_read_shipment if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_create_shipment if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_update_shipment if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
can_delete_shipment if {
	input.auth.authenticated == true
	input.auth.principal.kind == "user"
}
# End policy f45cinlkkxea
default allow := false

# Static definition Application Root
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /
	count(input.request.path) == 0
}
# Static definition HAL Explorer (old)
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /explorer/*
	count(input.request.path) >= 1
	input.request.path[0] == "explorer"
}
# Static definition HAL Explorer
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /webjars/hal-explorer/*
	count(input.request.path) >= 2
	input.request.path[0] == "webjars"
	input.request.path[1] == "hal-explorer"
}
# Static definition Swagger UI
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /webjars/swagger-ui/*
	count(input.request.path) >= 2
	input.request.path[0] == "webjars"
	input.request.path[1] == "swagger-ui"
}
# Static definition OpenAPI Spec
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /openapi.yml
	count(input.request.path) == 1
	input.request.path[0] == "openapi.yml"
}
# Static definition Dynamic HAL profiles
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /profile/*
	count(input.request.path) >= 1
	input.request.path[0] == "profile"
}
# Static definition Automations
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /.contentgrid/automations
	count(input.request.path) == 2
	input.request.path[0] == ".contentgrid"
	input.request.path[1] == "automations"
	input.auth.kind == "system"
	input.auth.principal.kind == "extension"
	input.entity.system == input.auth.principal.sub
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /.contentgrid/automations/{id}
	count(input.request.path) == 3
	input.request.path[0] == ".contentgrid"
	input.request.path[1] == "automations"
	# variable component {id}
	input.auth.kind == "system"
	input.auth.principal.kind == "extension"
	input.entity.system == input.auth.principal.sub
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /invoices
	count(input.request.path) == 1
	input.request.path[0] == "invoices"
	can_read_invoice == true
}
allow if {
	input.request.method == "POST"
	# Path /invoices
	count(input.request.path) == 1
	input.request.path[0] == "invoices"
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_create_invoice == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /invoices/{id}
	count(input.request.path) == 2
	input.request.path[0] == "invoices"
	# variable component {id}
	can_read_invoice == true
}
allow if {
	input.request.method == "PUT"
	# Path /invoices/{id}
	count(input.request.path) == 2
	input.request.path[0] == "invoices"
	# variable component {id}
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_update_invoice == true
}
allow if {
	input.request.method == "PATCH"
	# Path /invoices/{id}
	count(input.request.path) == 2
	input.request.path[0] == "invoices"
	# variable component {id}
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_update_invoice == true
}
allow if {
	input.request.method == "DELETE"
	# Path /invoices/{id}
	count(input.request.path) == 2
	input.request.path[0] == "invoices"
	# variable component {id}
	can_delete_invoice == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /invoices/{id}/shipments/{itemId}
	count(input.request.path) == 4
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "shipments"
	# variable component {itemId}
	can_read_invoice == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /invoices/{id}/shipments
	count(input.request.path) == 3
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "shipments"
	can_read_invoice == true
}
allow if {
	input.request.method == "POST"
	# Path /invoices/{id}/shipments
	count(input.request.path) == 3
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "shipments"
	util.request.content_type_in(["text/uri-list"])
	can_update_invoice == true
}
allow if {
	input.request.method == "DELETE"
	# Path /invoices/{id}/shipments/{itemId}
	count(input.request.path) == 4
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "shipments"
	# variable component {itemId}
	can_update_invoice == true
}
allow if {
	input.request.method == "DELETE"
	# Path /invoices/{id}/shipments
	count(input.request.path) == 3
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "shipments"
	can_update_invoice == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /invoices/{id}/customer
	count(input.request.path) == 3
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "customer"
	can_read_invoice == true
}
allow if {
	input.request.method == "PUT"
	# Path /invoices/{id}/customer
	count(input.request.path) == 3
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "customer"
	util.request.content_type_in(["text/uri-list"])
	can_update_invoice == true
}
allow if {
	input.request.method == "DELETE"
	# Path /invoices/{id}/customer
	count(input.request.path) == 3
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "customer"
	can_update_invoice == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /invoices/{id}/content
	count(input.request.path) == 3
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "content"
	can_read_invoice == true
}
allow if {
	input.request.method == ["POST", "PUT", "DELETE"][_]
	# Path /invoices/{id}/content
	count(input.request.path) == 3
	input.request.path[0] == "invoices"
	# variable component {id}
	input.request.path[2] == "content"
	can_update_invoice == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /persons
	count(input.request.path) == 1
	input.request.path[0] == "persons"
	can_read_person == true
}
allow if {
	input.request.method == "POST"
	# Path /persons
	count(input.request.path) == 1
	input.request.path[0] == "persons"
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_create_person == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /persons/{id}
	count(input.request.path) == 2
	input.request.path[0] == "persons"
	# variable component {id}
	can_read_person == true
}
allow if {
	input.request.method == "PUT"
	# Path /persons/{id}
	count(input.request.path) == 2
	input.request.path[0] == "persons"
	# variable component {id}
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_update_person == true
}
allow if {
	input.request.method == "PATCH"
	# Path /persons/{id}
	count(input.request.path) == 2
	input.request.path[0] == "persons"
	# variable component {id}
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_update_person == true
}
allow if {
	input.request.method == "DELETE"
	# Path /persons/{id}
	count(input.request.path) == 2
	input.request.path[0] == "persons"
	# variable component {id}
	can_delete_person == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /persons/{id}/invoices/{itemId}
	count(input.request.path) == 4
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "invoices"
	# variable component {itemId}
	can_read_person == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /persons/{id}/invoices
	count(input.request.path) == 3
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "invoices"
	can_read_person == true
}
allow if {
	input.request.method == "POST"
	# Path /persons/{id}/invoices
	count(input.request.path) == 3
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "invoices"
	util.request.content_type_in(["text/uri-list"])
	can_update_person == true
}
allow if {
	input.request.method == "DELETE"
	# Path /persons/{id}/invoices/{itemId}
	count(input.request.path) == 4
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "invoices"
	# variable component {itemId}
	can_update_person == true
}
allow if {
	input.request.method == "DELETE"
	# Path /persons/{id}/invoices
	count(input.request.path) == 3
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "invoices"
	can_update_person == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /persons/{id}/friends/{itemId}
	count(input.request.path) == 4
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "friends"
	# variable component {itemId}
	can_read_person == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /persons/{id}/friends
	count(input.request.path) == 3
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "friends"
	can_read_person == true
}
allow if {
	input.request.method == "POST"
	# Path /persons/{id}/friends
	count(input.request.path) == 3
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "friends"
	util.request.content_type_in(["text/uri-list"])
	can_update_person == true
}
allow if {
	input.request.method == "DELETE"
	# Path /persons/{id}/friends/{itemId}
	count(input.request.path) == 4
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "friends"
	# variable component {itemId}
	can_update_person == true
}
allow if {
	input.request.method == "DELETE"
	# Path /persons/{id}/friends
	count(input.request.path) == 3
	input.request.path[0] == "persons"
	# variable component {id}
	input.request.path[2] == "friends"
	can_update_person == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /shipments
	count(input.request.path) == 1
	input.request.path[0] == "shipments"
	can_read_shipment == true
}
allow if {
	input.request.method == "POST"
	# Path /shipments
	count(input.request.path) == 1
	input.request.path[0] == "shipments"
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_create_shipment == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /shipments/{id}
	count(input.request.path) == 2
	input.request.path[0] == "shipments"
	# variable component {id}
	can_read_shipment == true
}
allow if {
	input.request.method == "PUT"
	# Path /shipments/{id}
	count(input.request.path) == 2
	input.request.path[0] == "shipments"
	# variable component {id}
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_update_shipment == true
}
allow if {
	input.request.method == "PATCH"
	# Path /shipments/{id}
	count(input.request.path) == 2
	input.request.path[0] == "shipments"
	# variable component {id}
	util.request.content_type_in(["application/json", "application/hal+json", "application/merge-patch+json", "multipart/form-data"])
	can_update_shipment == true
}
allow if {
	input.request.method == "DELETE"
	# Path /shipments/{id}
	count(input.request.path) == 2
	input.request.path[0] == "shipments"
	# variable component {id}
	can_delete_shipment == true
}
allow if {
	input.request.method == ["HEAD", "GET"][_]
	# Path /shipments/{id}/invoice
	count(input.request.path) == 3
	input.request.path[0] == "shipments"
	# variable component {id}
	input.request.path[2] == "invoice"
	can_read_shipment == true
}
allow if {
	input.request.method == "PUT"
	# Path /shipments/{id}/invoice
	count(input.request.path) == 3
	input.request.path[0] == "shipments"
	# variable component {id}
	input.request.path[2] == "invoice"
	util.request.content_type_in(["text/uri-list"])
	can_update_shipment == true
}
allow if {
	input.request.method == "DELETE"
	# Path /shipments/{id}/invoice
	count(input.request.path) == 3
	input.request.path[0] == "shipments"
	# variable component {id}
	input.request.path[2] == "invoice"
	can_update_shipment == true
}
