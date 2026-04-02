package com.contentgrid.appserver.autoconfigure.archive;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

class ArchiveExtractionBeanFactoryPostProcessor implements BeanFactoryPostProcessor,
        EnvironmentAware, ApplicationContextAware, DisposableBean, ArchiveExtractionTempDirProvider {

    private Environment environment;
    private ApplicationContext applicationContext;

    @Getter
    private Path tempDir;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String location = environment.getRequiredProperty("contentgrid.appserver.archive");
        Resource archiveResource = applicationContext.getResource(location);

        this.tempDir = extractZip(archiveResource);

        if (applicationContext instanceof DefaultResourceLoader drl) {
            drl.addProtocolResolver(new ArchiveProtocolResolver(tempDir));
        }
    }

    private Path extractZip(Resource archiveResource) {
        try {
            Path dir = Files.createTempDirectory("contentgrid-archive-");
            try (ZipInputStream zis = new ZipInputStream(archiveResource.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = dir.resolve(entry.getName()).normalize();
                    if (!entryPath.startsWith(dir)) {
                        throw new IOException("Invalid zip entry path: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        Files.copy(zis, entryPath);
                    }
                    zis.closeEntry();
                }
            }
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract archive: " + archiveResource.getDescription(), e);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (tempDir != null) {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
