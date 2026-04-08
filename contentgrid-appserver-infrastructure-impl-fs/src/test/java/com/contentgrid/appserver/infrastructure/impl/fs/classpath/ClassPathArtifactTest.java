package com.contentgrid.appserver.infrastructure.impl.fs.classpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryUnreadableException;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassPathArtifactTest {

    @Nested
    class FileSystemBacked {

        @TempDir
        static Path root;

        static ClassPathArtifact artifact;

        @BeforeAll
        static void setup() throws IOException {
            Files.createDirectories(root.resolve("config/sub"));
            Files.writeString(root.resolve("config/a.yaml"), "key: a");
            Files.writeString(root.resolve("config/b.yaml"), "key: b");
            Files.writeString(root.resolve("config/sub/c.yaml"), "key: c");

            var classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()});
            artifact = new ClassPathArtifact(classLoader, Path.of("config"));
        }

        @Test
        void load_readsEntry() throws ArtifactException, ArtifactEntryUnreadableException {
            try (var stream = artifact.load(Path.of("a.yaml")).getInputStream()) {
                assertThat(stream).hasContent("key: a");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void load_missingEntry_throwsArtifactEntryNotFoundException() {
            assertThatThrownBy(() -> artifact.load(Path.of("missing.yaml")))
                    .isInstanceOf(ArtifactEntryNotFoundException.class);
        }

        @Test
        void loadAll_returnsAllEntriesRecursively() throws ArtifactException {
            var entries = artifact.loadAll(Path.of(""));
            assertThat(entries).hasSize(3); // a.yaml, b.yaml, sub/c.yaml
        }

        @Test
        void loadAll_onSubDirectory_returnsOnlyEntriesUnderIt() throws ArtifactException {
            var entries = artifact.loadAll(Path.of("sub"));
            assertThat(entries).hasSize(1);
        }
    }

    @Nested
    class JarBacked {

        @TempDir
        static Path tempDir;

        static ClassPathArtifact artifact;

        @BeforeAll
        static void setup() throws IOException {
            var jarPath = tempDir.resolve("test.jar");
            try (var jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
                addEntry(jos, "config/", null);
                addEntry(jos, "config/a.yaml", "key: a");
                addEntry(jos, "config/b.yaml", "key: b");
                addEntry(jos, "config/sub/", null);
                addEntry(jos, "config/sub/c.yaml", "key: c");
            }

            var classLoader = new URLClassLoader(new java.net.URL[]{jarPath.toUri().toURL()});
            artifact = new ClassPathArtifact(classLoader, Path.of("config"));
        }

        private static void addEntry(JarOutputStream jos, String name, String content) throws IOException {
            jos.putNextEntry(new JarEntry(name));
            if (content != null) {
                jos.write(content.getBytes());
            }
            jos.closeEntry();
        }

        @Test
        void load_readsEntry() throws ArtifactException, ArtifactEntryUnreadableException {
            try (var stream = artifact.load(Path.of("a.yaml")).getInputStream()) {
                assertThat(stream).hasContent("key: a");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void loadAll_returnsAllEntriesRecursively() throws ArtifactException {
            var entries = artifact.loadAll(Path.of(""));
            assertThat(entries).hasSize(3); // a.yaml, b.yaml, sub/c.yaml
        }

        @Test
        void loadAll_onSubDirectory_returnsOnlyEntriesUnderIt() throws ArtifactException {
            var entries = artifact.loadAll(Path.of("sub"));
            assertThat(entries).hasSize(1);
        }
    }
}
