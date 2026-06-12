package com.contentgrid.appserver.blueprintartifact.impl.utils;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ZipUtils {

    public static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        if (content != null) {
            zos.write(content.getBytes());
        }
        zos.closeEntry();
    }

}
