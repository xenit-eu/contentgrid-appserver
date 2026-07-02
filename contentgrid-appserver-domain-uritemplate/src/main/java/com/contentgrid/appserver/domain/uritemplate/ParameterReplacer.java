package com.contentgrid.appserver.domain.uritemplate;

public interface ParameterReplacer<S extends Enum<S> & SubstitutionVariableDefinition> {
    String replace(S substitutionVariable);

}
