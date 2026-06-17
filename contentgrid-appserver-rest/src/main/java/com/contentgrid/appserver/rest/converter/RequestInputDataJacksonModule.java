package com.contentgrid.appserver.rest.converter;

import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ScalarDataEntry;
import com.contentgrid.appserver.domain.data.RequestInputData;
import com.contentgrid.appserver.rest.data.ConversionServiceRequestInputData;
import com.contentgrid.appserver.rest.data.JsonRequestInputData;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ser.std.StdSerializer;

@Component
public class RequestInputDataJacksonModule extends SimpleModule {

    public RequestInputDataJacksonModule(ObjectProvider<ConversionService> conversionService) {
        addDeserializer(RequestInputData.class, new RequestInputDataDeserializer(conversionService::getObject));
        addSerializer(PlainDataEntry.class, new PlainDataEntrySerializer());
    }

    private static class PlainDataEntrySerializer extends StdSerializer<PlainDataEntry> {

        public PlainDataEntrySerializer() {
            super(PlainDataEntry.class);
        }

        @Override
        public void serialize(PlainDataEntry value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            switch (value) {
                case MapDataEntry mapDataEntry -> {
                    gen.writeStartObject(mapDataEntry, mapDataEntry.getItems().size());
                    for (var entry : mapDataEntry.getItems().entrySet()) {
                        gen.writeName(entry.getKey());
                        serialize(entry.getValue(), gen, ctxt);
                    }
                    gen.writeEndObject();
                }
                case NullDataEntry ignored -> gen.writeNull();
                case ScalarDataEntry scalarDataEntry -> {
                    var primitiveValue = scalarDataEntry.getValue();
                    ctxt.findValueSerializer(primitiveValue.getClass()).serialize(primitiveValue, gen, ctxt);
                }
                case ListDataEntry listDataEntry -> {
                    gen.writeStartArray(listDataEntry, listDataEntry.getItems().size());
                    for (var entry : listDataEntry.getItems()) {
                        serialize(entry, gen, ctxt);
                    }
                    gen.writeEndArray();
                }
                case MissingDataEntry ignored -> {
                    // Do not write anything for missing data
                }
            }
        }
    }

    private static class RequestInputDataDeserializer extends StdDeserializer<RequestInputData> {
        private final Supplier<ConversionService> conversionServiceSupplier;

        public RequestInputDataDeserializer(Supplier<ConversionService> conversionServiceSupplier) {
            super(RequestInputDataDeserializer.class);
            this.conversionServiceSupplier = conversionServiceSupplier;
        }

        @Override
        public RequestInputData deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            return new ConversionServiceRequestInputData(new JsonRequestInputData(p.readValueAs(ObjectNode.class)), conversionServiceSupplier.get());
        }
    }
}
