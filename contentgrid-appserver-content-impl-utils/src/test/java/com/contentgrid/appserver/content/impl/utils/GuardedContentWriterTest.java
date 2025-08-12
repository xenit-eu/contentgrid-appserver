package com.contentgrid.appserver.content.impl.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.content.api.UnwritableContentException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GuardedContentWriterTest {

    @Test
    void openOutputStreamAtMostOnce() throws IOException, UnwritableContentException {
        var guarded = new GuardedContentWriter(new FakeContentWriter());
        guarded.getContentOutputStream().close();

        assertThrows(IllegalStateException.class, guarded::getContentOutputStream);
    }
    @Test
    void getInformationBeforeContentWrite() {
        var guarded = new GuardedContentWriter(new FakeContentWriter());

        assertThrows(IllegalStateException.class, guarded::getReference);
        assertThrows(IllegalStateException.class, guarded::getContentSize);
    }

    @Test
    void getInformationAfterContentWriteBeforeClose() throws IOException, UnwritableContentException {
        var guarded = new GuardedContentWriter(new FakeContentWriter());
        try(var os = guarded.getContentOutputStream()) {
            os.write(4);
            assertThrows(IllegalStateException.class, guarded::getReference);
            assertThrows(IllegalStateException.class, guarded::getContentSize);
        }

    }

    @Test
    void getInformationAfterContentWriteAndClose() throws IOException, UnwritableContentException {
        var guarded = new GuardedContentWriter(new FakeContentWriter());
        try(var os = guarded.getContentOutputStream()) {
            os.write(4);
        }

        assertDoesNotThrow(guarded::getReference);
        assertDoesNotThrow(guarded::getContentSize);
    }

}