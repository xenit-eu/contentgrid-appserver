package com.contentgrid.appserver.autoconfigure.archive;

import java.nio.file.Path;

public interface ArchiveExtractionTempDirProvider {

    Path getTempDir();

}
