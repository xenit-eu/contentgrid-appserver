# Policy used by OpaResidualAuthorizationTest to exercise the residual-authorization behavior of the second
# rule below. It lives at the blueprint artifact's rego/policy.rego, so the OPA container in that test pulls it
# as a bundle from /actuator/policybundle rather than being handed it by the test.
package ${system.policy.package}

import rego.v1

default allow := false

# Any authenticated user may create customers, so tests can seed data through the REST API.
# This rule never touches input.entity, so partial evaluation resolves it immediately - no residual.
allow if {
	input.auth.authenticated == true
	input.request.method == "POST"
	input.request.path == ["customers"]
}

# Authenticated users may only see customers whose total_spend is at most 100.
# input.entity is the declared 'unknown' during partial evaluation, so this produces a residual
# expression (`entity.total_spend <= 100`) instead of resolving to a plain boolean.
allow if {
	input.auth.authenticated == true
	input.request.method == "GET"
	input.request.path[0] == "customers"
	input.entity.total_spend <= 100
}
