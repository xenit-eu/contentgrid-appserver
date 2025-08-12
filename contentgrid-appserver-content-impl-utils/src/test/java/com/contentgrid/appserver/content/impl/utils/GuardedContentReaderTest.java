package com.contentgrid.appserver.content.impl.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.content.api.UnreadableContentException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GuardedContentReaderTest {
    @Test
    void openInputStreamAtMostOnce() throws IOException, UnreadableContentException {
        var guarded = new GuardedContentReader(new BytesContentReader(new byte[] {}));
        guarded.getContentInputStream().close();

        assertThrows(IllegalStateException.class, guarded::getContentInputStream);
    }

}