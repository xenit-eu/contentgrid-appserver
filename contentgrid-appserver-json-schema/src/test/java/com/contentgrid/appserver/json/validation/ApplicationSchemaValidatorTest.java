package com.contentgrid.appserver.json.validation;


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

        var metaSchemaValidation = JsonSchemaFactory.getInstance(VersionFlag.V7)
                .getSchema(SchemaLocation.of(SchemaId.V7))
                .validate(schema.getSchemaNode());
        assertThat(metaSchemaValidation, empty());
    }

}