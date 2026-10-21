package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilTest {
    @TempDir(factory = WorkspaceTempDirFactory.class)
    Path tempDir;

    @Test
    void listDirIncludesNestedFilesOnlyWhenRecursive() throws IOException {
        Path direct = Files.writeString(tempDir.resolve("direct.txt"), "direct");
        Path nestedDir = Files.createDirectories(tempDir.resolve("nested/deeper"));
        Path nested = Files.writeString(nestedDir.resolve("nested.txt"), "nested");

        assertEquals(Set.of(direct), Set.copyOf(FileUtil.listDir(false, tempDir.toString())));
        assertEquals(Set.of(direct, nested), Set.copyOf(FileUtil.listDir(true, tempDir.toString())));
    }

    @Test
    void deleteRemovesFilesAndDirectoriesIncludingEmptyOnes() throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Files.createDirectories(root.resolve("nested/empty"));
        Files.writeString(root.resolve("nested/file.txt"), "content");
        Path empty = Files.createDirectory(tempDir.resolve("empty"));
        Path file = Files.writeString(tempDir.resolve("file.txt"), "content");
        Path sibling = Files.writeString(tempDir.resolve("keep.txt"), "keep");

        FileUtil.deleteDirRecursion(root.toString(), empty.toString(), file.toString(),
                tempDir.resolve("missing").toString());

        assertFalse(Files.exists(root));
        assertFalse(Files.exists(empty));
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(sibling));
    }
    static class WorkspaceTempDirFactory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext elementContext, ExtensionContext extensionContext)
                throws IOException {
            Path parent = Files.createDirectories(Path.of("build", "tmp", "file-util-test").toAbsolutePath());
            return Files.createTempDirectory(parent, "junit-");
        }
    }

}
