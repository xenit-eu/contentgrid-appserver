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
import com.contentgrid.appserver.rest.data.conversion.LongDataEntryToDecimalDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToDecimalDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToInstantDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToLocalDateDataEntryConverter;
import com.contentgrid.appserver.rest.data.conversion.StringDataEntryToLongDataEntryConverter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.stereotype.Component;

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
        public void serialize(PlainDataEntry value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            switch (value) {
                case MapDataEntry mapDataEntry -> {
                    gen.writeStartObject(mapDataEntry, mapDataEntry.getItems().size());
                    for (var entry : mapDataEntry.getItems().entrySet()) {
                        gen.writeFieldName(entry.getKey());
                        serialize(entry.getValue(), gen, provider);
                    }
                    gen.writeEndObject();
                }
                case NullDataEntry ignored -> gen.writeNull();
                case ScalarDataEntry scalarDataEntry -> {
                    var primitiveValue = scalarDataEntry.getValue();
                    provider.findValueSerializer(primitiveValue.getClass()).serialize(primitiveValue, gen, provider);
                }
                case ListDataEntry listDataEntry -> {
                    gen.writeStartArray(listDataEntry, listDataEntry.getItems().size());
                    for (var entry : listDataEntry.getItems()) {
                        serialize(entry, gen, provider);
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
        private ConversionService jsonConversionService;

        public RequestInputDataDeserializer(Supplier<ConversionService> conversionServiceSupplier) {
            super(RequestInputDataDeserializer.class);
            this.conversionServiceSupplier = conversionServiceSupplier;
        }

        private ConversionService getJsonConversionService() {
            if(this.jsonConversionService == null) {
                var conversionService = conversionServiceSupplier.get();
                var jsonConversionService = new GenericConversionService();
                jsonConversionService.addConverter(new StringDataEntryToDecimalDataEntryConverter(conversionService));
                jsonConversionService.addConverter(new StringDataEntryToLongDataEntryConverter(conversionService));
                jsonConversionService.addConverter(new StringDataEntryToInstantDataEntryConverter(conversionService));
                jsonConversionService.addConverter(new StringDataEntryToLocalDateDataEntryConverter(conversionService));
                jsonConversionService.addConverter(new LongDataEntryToDecimalDataEntryConverter());
                this.jsonConversionService = jsonConversionService;
            }
            return this.jsonConversionService;
        }

        @Override
        public RequestInputData deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            return new ConversionServiceRequestInputData(new JsonRequestInputData(p.readValueAs(ObjectNode.class)), getJsonConversionService());
        }
    }
}
