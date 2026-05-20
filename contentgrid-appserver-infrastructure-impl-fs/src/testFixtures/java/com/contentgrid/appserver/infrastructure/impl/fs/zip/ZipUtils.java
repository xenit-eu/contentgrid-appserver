package com.contentgrid.appserver.infrastructure.impl.fs.zip;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ZipUtils {

    public static void createZip(@NonNull Path path) throws IOException {
        try (var zos = new ZipOutputStream(new FileOutputStream(path.toFile()))) {
            addEntry(zos, "file.txt", "hello");
        }
    }

    public static void addEntry(@NonNull ZipOutputStream zos, @NonNull String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        if (content != null) {
            zos.write(content.getBytes());
        }
        zos.closeEntry();
    }
}
