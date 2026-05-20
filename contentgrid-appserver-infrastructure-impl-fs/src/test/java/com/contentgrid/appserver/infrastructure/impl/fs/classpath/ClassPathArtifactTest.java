package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import com.contentgrid.appserver.infrastructure.api.AbstractArtifactTest;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

class ClassPathArtifactTest {

    @Nested
    class FileSystemBacked extends AbstractArtifactTest {

        @TempDir
        static Path root;

        static ClassPathArtifact artifact;

        @BeforeAll
        static void setup() throws IOException {
            Files.createDirectories(root.resolve("test/config/sub"));
            Files.writeString(root.resolve("test/file.txt"), "hello");
            Files.writeString(root.resolve("test/config/a.yaml"), "key: a");
            Files.writeString(root.resolve("test/config/b.yaml"), "key: b");
            Files.writeString(root.resolve("test/config/sub/c.yaml"), "key: c");

            var classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()});
            artifact = new ClassPathArtifact(classLoader, Path.of("test"));
        }

        @AfterAll
        static void cleanup() {
            artifact.close();
        }

        @Override
        protected Artifact getArtifact() {
            return artifact;
        }
    }

    @Nested
    class JarBacked extends AbstractArtifactTest {

        @TempDir
        static Path tempDir;

        static ClassPathArtifact artifact;

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
            artifact = new ClassPathArtifact(classLoader, Path.of("test"));
        }

        private static void addEntry(JarOutputStream jos, String name, String content) throws IOException {
            jos.putNextEntry(new JarEntry(name));
            if (content != null) {
                jos.write(content.getBytes());
            }
            jos.closeEntry();
        }

        @AfterAll
        static void cleanup() {
            artifact.close();
        }

        @Override
        protected Artifact getArtifact() {
            return artifact;
        }
    }
}
