package com.contentgrid.appserver.domain.data.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.domain.data.DataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.MissingDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidPropertyDataException;
import com.contentgrid.appserver.domain.data.MapRequestInputData;
import com.contentgrid.appserver.domain.data.mapper.AuditAttributeMapper.Mode;
import com.contentgrid.appserver.domain.data.type.DataType;
import com.contentgrid.appserver.domain.data.type.TechnicalDataType;
import com.contentgrid.appserver.domain.data.validation.AttributeValidationDataMapper;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MultivalueAttributeMappingTest {

    private static final MultivalueAttribute TAGS = MultivalueAttribute.builder()
            .name(AttributeName.of("tags"))
            .column(ColumnName.of("tags"))
            .itemType(Type.TEXT)
            .build();

    private static ListDataEntry listOf(String... values) {
        return new ListDataEntry(Arrays.stream(values)
                .map(value -> (PlainDataEntry) new StringDataEntry(value))
                .toList());
    }

    @Test
    void requestInputListMapsToListDataEntry() throws InvalidPropertyDataException {
        var mapper = new RequestInputDataToDataEntryMapper();

        var result = mapper.mapAttribute(TAGS, MapRequestInputData.fromMap(Map.of("tags", List.of("a", "b"))));

        assertThat(result).contains(listOf("a", "b"));
    }

    @Test
    void requestInputNullMapsToNullDataEntry() throws InvalidPropertyDataException {
        var mapper = new RequestInputDataToDataEntryMapper();
        var data = new HashMap<String, Object>();
        data.put("tags", null);

        var result = mapper.mapAttribute(TAGS, MapRequestInputData.fromMap(data));

        assertThat(result).contains(NullDataEntry.INSTANCE);
    }

    @Test
    void requestInputMissingMapsToMissingDataEntry() throws InvalidPropertyDataException {
        var mapper = new RequestInputDataToDataEntryMapper();

        var result = mapper.mapAttribute(TAGS, MapRequestInputData.fromMap(Map.of()));

        assertThat(result).contains(MissingDataEntry.INSTANCE);
    }

    @Test
    void requestInputScalarIsRejected() {
        var mapper = new RequestInputDataToDataEntryMapper();

        assertThatThrownBy(() -> mapper.mapAttribute(TAGS, MapRequestInputData.fromMap(Map.of("tags", "urgent"))))
                .isInstanceOf(InvalidPropertyDataException.class)
                .hasMessageContaining("string_set");
    }

    @Test
    void requestInputNullElementIsRejected() {
        var mapper = new RequestInputDataToDataEntryMapper();
        var input = MapRequestInputData.fromMap(Map.of("tags", Arrays.asList("a", null)));

        assertThatThrownBy(() -> mapper.mapAttribute(TAGS, input))
                .isInstanceOf(InvalidPropertyDataException.class);
    }

    @Test
    void listDataEntryMapsToListValue() throws InvalidPropertyDataException {
        var mapper = new DataEntryToQueryEngineMapper();

        var result = mapper.mapAttribute(TAGS, listOf("a", "b"));

        assertThat(result).hasValueSatisfying(attributeData -> {
            var simpleAttributeData = (SimpleAttributeData<?>) attributeData;
            assertEquals(AttributeName.of("tags"), simpleAttributeData.getName());
            assertEquals(List.of("a", "b"), simpleAttributeData.getValue());
        });
    }

    @Test
    void nullDataEntryCanonicalizesToEmptyList() throws InvalidPropertyDataException {
        var mapper = new DataEntryToQueryEngineMapper();

        var result = mapper.mapAttribute(TAGS, NullDataEntry.INSTANCE);

        assertThat(result).hasValueSatisfying(attributeData ->
                assertEquals(List.of(), ((SimpleAttributeData<?>) attributeData).getValue()));
    }

    @Test
    void scalarDataEntryIsRejectedByQueryEngineMapper() {
        var mapper = new DataEntryToQueryEngineMapper();

        assertThatThrownBy(() -> mapper.mapAttribute(TAGS, new StringDataEntry("urgent")))
                .isInstanceOf(InvalidPropertyDataException.class);
    }

    @Test
    void attributeDataListMapsToListDataEntry() {
        var mapper = new AttributeDataToDataEntryMapper();
        var data = SimpleAttributeData.builder().name(AttributeName.of("tags")).value(List.of("a", "b")).build();

        var result = mapper.mapAttribute(TAGS, Optional.of(data));

        assertEquals(listOf("a", "b"), result);
    }

    @Test
    void attributeDataNullValueMapsToEmptyListDataEntry() {
        var mapper = new AttributeDataToDataEntryMapper();
        var data = SimpleAttributeData.builder().name(AttributeName.of("tags")).value(null).build();

        var result = mapper.mapAttribute(TAGS, Optional.of(data));

        assertEquals(new ListDataEntry(List.of()), result);
    }

    @Test
    void absentAttributeDataMapsToEmptyListDataEntry() {
        var mapper = new AttributeDataToDataEntryMapper();

        var result = mapper.mapAttribute(TAGS, Optional.empty());

        assertEquals(new ListDataEntry(List.of()), result);
    }

    @Test
    void auditMapperPassesMultivalueDataThrough() throws InvalidPropertyDataException {
        var mapper = new AuditAttributeMapper(Mode.UPDATE, null, Clock.systemUTC());
        var input = Optional.<DataEntry>of(listOf("a"));

        var result = mapper.mapAttribute(TAGS, input);

        assertEquals(input, result);
    }

    @Test
    void dataTypeOfMultivalueAttributeIsStringSet() {
        assertEquals(TechnicalDataType.STRING_SET, DataType.of(TAGS));
        assertEquals(TechnicalDataType.STRING_SET, DataType.of((Attribute) TAGS));
    }

    @Test
    void validatorsReceiveMultivalueAttribute() throws InvalidPropertyDataException {
        var seen = new ArrayList<Attribute>();
        var mapper = new AttributeValidationDataMapper((path, attribute, dataEntry) -> seen.add(attribute));

        var result = mapper.mapAttribute(TAGS, listOf("a"));

        assertThat(seen).containsExactly(TAGS);
        assertThat(result).contains(listOf("a"));
    }
}
