package com.contentgrid.appserver.content.impl.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import lombok.NonNull;

public class CloseCallbackOutputStream extends FilterOutputStream {
    @NonNull
    private final Runnable onCloseCallback;

    /**
     * Creates an output stream filter built on top of the specified underlying output stream.
     *
     * @param out the underlying output stream to be assigned to the field {@code this.out} for later use, or {@code null}
     * @param onCloseCallback function that is called after the stream has been closed
     * if this instance is to be created without an underlying stream.
     */
    public CloseCallbackOutputStream(@NonNull OutputStream out, @NonNull Runnable onCloseCallback) {
        super(out);
        this.onCloseCallback = onCloseCallback;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // Overridden for performance, since we don't override write(int)
        out.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        onCloseCallback.run();
    }
}
