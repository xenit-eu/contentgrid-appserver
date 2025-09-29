# Note: ${system.policy.package} is automatically replaced with the package name.
# Replace it when deploying this file to OPA directly.
package ${system.policy.package}

util.extract_content_type(header) := content_type {
	mime_type := trim_space(split(header, ";")[0])
	content_type := lower(mime_type)
}
default util.content_type_in(headers, accepted_content_types) := false

util.content_type_in(headers, accepted_content_types) {
	count(headers) == 1
	extracted_mime_type := util.extract_content_type(headers[0])
	extracted_mime_type == accepted_content_types[_]
}
default util.request.content_type_in(content_types) := false

util.request.content_type_in(content_types) {
	util.content_type_in(input.request.headers["content-type"], content_types)
}
