package com.contentgrid.appserver.application.model.json.validation;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaId;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion.VersionFlag;
import org.junit.jupiter.api.Test;

class ApplicationSchemaValidatorTest {
    @Test
    void jsonSchemaIsValid() {
        var schema = ApplicationSchemaValidator.schema;

        var metaSchemaValidation = JsonSchemaFactory.getInstance(VersionFlag.V202012)
                .getSchema(SchemaLocation.of(SchemaId.V202012))
                .validate(schema.getSchemaNode());
        assertThat(metaSchemaValidation, empty());
    }

}