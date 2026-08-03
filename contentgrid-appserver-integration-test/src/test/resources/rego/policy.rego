# Blueprint-artifact policy picked up by OpaPolicyUploader (which loads rego/policy.rego from the
# blueprint artifact root).
package ${system.policy.package}

import rego.v1

default allow := false

allow if {
	input.auth.authenticated == true
}
