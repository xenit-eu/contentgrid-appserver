package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.json.DefaultApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.json.exceptions.InvalidJsonException;
import java.io.InputStream;
import org.springframework.util.function.ThrowingSupplier;

public class InputStreamSupplierApplicationResolver extends SingleApplicationResolver {

    public InputStreamSupplierApplicationResolver(ThrowingSupplier<InputStream> inputStreamSupplier) throws InvalidJsonException {
        super(loadApplication(inputStreamSupplier));
    }

    private static Application loadApplication(ThrowingSupplier<InputStream> inputStreamSupplier) throws InvalidJsonException {
        var converter = new DefaultApplicationSchemaConverter();
        return converter.convert(inputStreamSupplier.get());
    }
}
