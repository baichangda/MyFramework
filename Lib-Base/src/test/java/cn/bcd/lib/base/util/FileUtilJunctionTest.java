package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实的 Windows 目录联接验证遍历边界和删除行为。
 */
@EnabledOnOs(OS.WINDOWS)
class FileUtilJunctionTest {
    @TempDir(factory = FileUtilTest.WorkspaceTempDirFactory.class)
    Path tempDir;

    /** 在测试目录中创建指向指定目标的目录联接。 */
    private Path junction(Path link, Path target) throws Exception {
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                link.toString(), target.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
        return link;
    }

    /** 确认删除路径位于当前测试的临时目录中，再执行删除。 */
    private void deleteWithinTempDir(Path path) {
        assertTrue(path.toAbsolutePath().normalize().startsWith(tempDir.toAbsolutePath().normalize()));
        FileUtil.deleteDirRecursion(path.toString());
    }

    /** 验证遍历会跳过指向外部目录的联接，以及作为根路径传入的联接。 */
    @Test
    void listingSkipsExternalJunctionAndJunctionRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path local = Files.writeString(root.resolve("local.txt"), "local");
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("sentinel.txt"), "keep");
        Path link = junction(root.resolve("link"), outside);
        try {
            for (boolean recursion : new boolean[]{false, true}) {
                assertEquals(List.of(local), FileUtil.listDir(recursion, root.toString()));
                assertTrue(FileUtil.listDir(recursion, link.toString()).isEmpty());
            }
            assertEquals("keep", Files.readString(sentinel));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    /** 验证目录联接别名不会导致同一文件被重复返回。 */
    @Test
    void listingDoesNotDuplicateFilesThroughJunctionAlias() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path child = Files.createDirectory(root.resolve("child"));
        Path file = Files.writeString(child.resolve("file.txt"), "keep");
        Path link = junction(root.resolve("alias"), child);
        try {
            assertEquals(List.of(file), FileUtil.listDir(true, root.toString()));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    /** 验证循环联接被跳过，普通子目录仍按递归参数正常遍历。 */
    @Test
    void listingSkipsCyclicJunctionAndPreservesOrdinarySubdirectories() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path child = Files.createDirectory(root.resolve("child"));
        Path first = Files.writeString(root.resolve("first.txt"), "first");
        Path second = Files.writeString(child.resolve("second.txt"), "second");
        Path link = junction(child.resolve("back"), root);
        try {
            assertEquals(Set.of(first, second), Set.copyOf(FileUtil.listDir(true, root.toString())));
            assertEquals(List.of(first), FileUtil.listDir(false, root.toString()));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    /** 验证删除含有目录联接的目录时，联接目标中的文件得到保留。 */
    @Test
    void deletingDirectoryWithJunctionPreservesExternalTarget() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Files.writeString(root.resolve("local.txt"), "delete");
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("sentinel.txt"), "keep");
        Path link = junction(root.resolve("link"), outside);
        try {
            deleteWithinTempDir(root);
            assertFalse(Files.exists(root, LinkOption.NOFOLLOW_LINKS));
            assertEquals("keep", Files.readString(sentinel));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    /** 验证直接删除目录联接时，仅删除联接本身。 */
    @Test
    void deletingJunctionRootPreservesTarget() throws Exception {
        Path target = Files.createDirectory(tempDir.resolve("target"));
        Path sentinel = Files.writeString(target.resolve("sentinel.txt"), "keep");
        Path link = junction(tempDir.resolve("link"), target);
        try {
            deleteWithinTempDir(link);
            assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS));
            assertEquals("keep", Files.readString(sentinel));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    /** 验证目标不存在的目录联接可被跳过或删除，包括根路径和子项两种情况。 */
    @Test
    void danglingJunctionCanBeListedAndDeletedAsRootOrChild() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path target = Files.createDirectory(tempDir.resolve("target"));
        Path child = junction(root.resolve("link"), target);
        Path link = junction(tempDir.resolve("link"), target);
        Files.delete(target);
        try {
            assertTrue(FileUtil.listDir(true, root.toString()).isEmpty());
            assertTrue(FileUtil.listDir(true, link.toString()).isEmpty());
            deleteWithinTempDir(link);
            deleteWithinTempDir(root);
            assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS));
            assertFalse(Files.exists(root, LinkOption.NOFOLLOW_LINKS));
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(link);
        }
    }

    /** 验证含循环联接的目录能够正常删除，避免反复进入祖先目录。 */
    @Test
    void deletingDirectoryWithCyclicJunctionTerminates() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path child = Files.createDirectory(root.resolve("child"));
        Files.writeString(child.resolve("file.txt"), "delete");
        Path link = junction(child.resolve("back"), root);
        try {
            deleteWithinTempDir(root);
            assertFalse(Files.exists(root, LinkOption.NOFOLLOW_LINKS));
        } finally {
            Files.deleteIfExists(link);
        }
    }
}
