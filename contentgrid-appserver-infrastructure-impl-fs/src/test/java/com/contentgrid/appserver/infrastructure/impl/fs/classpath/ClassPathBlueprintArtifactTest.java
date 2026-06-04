package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.AbstractBlueprintArtifactTest;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

class ClassPathBlueprintArtifactTest {

    @Nested
    class FileSystemBacked extends AbstractBlueprintArtifactTest {

        @TempDir
        static Path root;

        static ClassPathBlueprintArtifact blueprintArtifact;

        @BeforeAll
        static void setup() throws IOException {
            Files.createDirectories(root.resolve("test/config/sub"));
            Files.writeString(root.resolve("test/file.txt"), "hello");
            Files.writeString(root.resolve("test/config/a.yaml"), "key: a");
            Files.writeString(root.resolve("test/config/b.yaml"), "key: b");
            Files.writeString(root.resolve("test/config/sub/c.yaml"), "key: c");

            var classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()});
            blueprintArtifact = new ClassPathBlueprintArtifact(classLoader, Path.of("test"));
        }

        @Override
        protected BlueprintArtifact getBlueprintArtifact() {
            return blueprintArtifact;
        }
    }

    @Nested
    class JarBacked extends AbstractBlueprintArtifactTest {

        @TempDir
        static Path tempDir;

        static ClassPathBlueprintArtifact blueprintArtifact;

        @BeforeAll
        static void setup() throws IOException {
            var jarPath = tempDir.resolve("test.jar");
            try (var jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
                addEntry(jos, "test/", null);
                addEntry(jos, "test/file.txt", "hello");
                addEntry(jos, "test/config/", null);
                addEntry(jos, "test/config/a.yaml", "key: a");
                addEntry(jos, "test/config/b.yaml", "key: b");
                addEntry(jos, "test/config/sub/", null);
                addEntry(jos, "test/config/sub/c.yaml", "key: c");
            }

            var classLoader = new URLClassLoader(new java.net.URL[]{jarPath.toUri().toURL()});
            blueprintArtifact = new ClassPathBlueprintArtifact(classLoader, Path.of("test"));
        }

        private static void addEntry(JarOutputStream jos, String name, String content) throws IOException {
            jos.putNextEntry(new JarEntry(name));
            if (content != null) {
                jos.write(content.getBytes());
            }
            jos.closeEntry();
        }

        @Override
        protected BlueprintArtifact getBlueprintArtifact() {
            return blueprintArtifact;
        }
    }
}
