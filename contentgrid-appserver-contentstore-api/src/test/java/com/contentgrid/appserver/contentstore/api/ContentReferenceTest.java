package com.contentgrid.appserver.contentstore.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ContentReferenceTest {

    @Test
    void testCreateWithoutStoreId() {
        var reference = ContentReference.of("content-123");

        assertThat(reference.getValue()).isEqualTo("content-123");
        assertThat(reference.getStoreId()).isNull();
    }

    @Test
    void testCreateWithStoreId() {
        var reference = ContentReference.of("mystore", "content-456");

        assertThat(reference.getValue()).isEqualTo("content-456");
        assertThat(reference.getStoreId()).isEqualTo("mystore");
    }

    @Test
    void testCreateWithNullValue() {
        assertThatThrownBy(() -> ContentReference.of(null)).isInstanceOf(
            NullPointerException.class
        );
    }

    @Test
    void testCreateWithNullStoreId() {
        assertThatThrownBy(() ->
            ContentReference.of(null, "content-123")
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateWithNullValueAndStoreId() {
        assertThatThrownBy(() ->
            ContentReference.of("store", null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testParseSimpleReference() {
        var reference = ContentReference.parse("content-789");

        assertThat(reference.getValue()).isEqualTo("content-789");
        assertThat(reference.getStoreId()).isNull();
    }

    @Test
    void testParseReferenceWithStoreId() {
        var reference = ContentReference.parse("mystore:content-012");

        assertThat(reference.getValue()).isEqualTo("content-012");
        assertThat(reference.getStoreId()).isEqualTo("mystore");
    }

    @Test
    void testParseReferenceWithMultipleColons() {
        // Only the first colon is used as separator
        var reference = ContentReference.parse("store:content:with:colons");

        assertThat(reference.getValue()).isEqualTo("content:with:colons");
        assertThat(reference.getStoreId()).isEqualTo("store");
    }

    @Test
    void testParseReferenceWithEmptyStoreId() {
        var reference = ContentReference.parse(":content-123");

        assertThat(reference.getValue()).isEqualTo("content-123");
        assertThat(reference.getStoreId()).isNull();
    }

    @Test
    void testParseReferenceWithColonAtEnd() {
        var reference = ContentReference.parse("content-123:");

        assertThat(reference.getValue()).isEqualTo("");
        assertThat(reference.getStoreId()).isEqualTo("content-123");
    }

    @Test
    void testParseNullReference() {
        assertThatThrownBy(() -> ContentReference.parse(null)).isInstanceOf(
            NullPointerException.class
        );
    }

    @Test
    void testToStorageFormatWithoutStoreId() {
        var reference = ContentReference.of("content-345");

        assertThat(reference.toStorageFormat()).isEqualTo("content-345");
    }

    @Test
    void testToStorageFormatWithStoreId() {
        var reference = ContentReference.of("mystore", "content-678");

        assertThat(reference.toStorageFormat()).isEqualTo(
            "mystore:content-678"
        );
    }

    @Test
    void testToStorageFormatWithEmptyStoreId() {
        var reference = ContentReference.parse(":content-123");

        // Empty store ID should result in just the value
        assertThat(reference.toStorageFormat()).isEqualTo("content-123");
    }

    @Test
    void testRoundTripWithoutStoreId() {
        var original = ContentReference.of("content-901");
        var parsed = ContentReference.parse(original.toStorageFormat());

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void testRoundTripWithStoreId() {
        var original = ContentReference.of("store1", "content-234");
        var parsed = ContentReference.parse(original.toStorageFormat());

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void testEquality() {
        var ref1 = ContentReference.of("store", "content-123");
        var ref2 = ContentReference.of("store", "content-123");
        var ref3 = ContentReference.of("other", "content-123");
        var ref4 = ContentReference.of("store", "content-456");
        var ref5 = ContentReference.of("content-123");

        assertThat(ref1).isEqualTo(ref2);
        assertThat(ref1).isNotEqualTo(ref3);
        assertThat(ref1).isNotEqualTo(ref4);
        assertThat(ref1).isNotEqualTo(ref5);
        assertThat(ref1).isNotEqualTo(null);
        assertThat(ref1).isNotEqualTo("string");
    }

    @Test
    void testHashCode() {
        var ref1 = ContentReference.of("store", "content-123");
        var ref2 = ContentReference.of("store", "content-123");
        var ref3 = ContentReference.of("other", "content-123");

        assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());
        // Different store ID should generally produce different hash
        assertThat(ref1.hashCode()).isNotEqualTo(ref3.hashCode());
    }

    @Test
    void testToStringWithoutStoreId() {
        var reference = ContentReference.of("content-567");

        assertThat(reference.toString()).isEqualTo("content-567");
    }

    @Test
    void testToStringWithStoreId() {
        var reference = ContentReference.of("mystore", "content-890");

        assertThat(reference.toString()).isEqualTo("mystore:content-890");
    }

    @Test
    void testSpecialCharactersInValue() {
        var reference = ContentReference.of(
            "store",
            "content/with/slashes-and-dashes_123"
        );

        assertThat(reference.getValue()).isEqualTo(
            "content/with/slashes-and-dashes_123"
        );
        assertThat(reference.toStorageFormat()).isEqualTo(
            "store:content/with/slashes-and-dashes_123"
        );
    }

    @Test
    void testSpecialCharactersInStoreId() {
        var reference = ContentReference.of("store-name_v2", "content-123");

        assertThat(reference.getStoreId()).isEqualTo("store-name_v2");
        assertThat(reference.toStorageFormat()).isEqualTo(
            "store-name_v2:content-123"
        );
    }

    @Test
    void testUUIDAsContent() {
        var uuid = "550e8400-e29b-41d4-a716-446655440000";
        var reference = ContentReference.of("store", uuid);

        assertThat(reference.getValue()).isEqualTo(uuid);
        assertThat(reference.toStorageFormat()).isEqualTo("store:" + uuid);
    }

    @Test
    void testBackwardCompatibility() {
        // Old references without store ID should still work
        var legacyFormat = "abc123def456";
        var reference = ContentReference.parse(legacyFormat);

        assertThat(reference.getValue()).isEqualTo(legacyFormat);
        assertThat(reference.getStoreId()).isNull();
        assertThat(reference.toStorageFormat()).isEqualTo(legacyFormat);
    }

    @Test
    void testMigrationScenario() {
        // Simulate migrating from old format to new format
        var oldReference = ContentReference.of("old-content-id");
        assertThat(oldReference.getStoreId()).isNull();
        assertThat(oldReference.toStorageFormat()).isEqualTo("old-content-id");

        var newReference = ContentReference.of("newstore", "new-content-id");
        assertThat(newReference.getStoreId()).isEqualTo("newstore");
        assertThat(newReference.toStorageFormat()).isEqualTo(
            "newstore:new-content-id"
        );
    }
}
