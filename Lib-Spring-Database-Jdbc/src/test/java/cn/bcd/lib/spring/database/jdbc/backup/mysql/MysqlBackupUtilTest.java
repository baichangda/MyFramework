package cn.bcd.lib.spring.database.jdbc.backup.mysql;

import cn.bcd.lib.base.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MysqlBackupUtilTest {
    @TempDir
    Path directory;

    public static class DumpProcess {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "success" -> {
                    System.out.print("CREATE TABLE example(id INT);");
                    // Exceeds a pipe buffer: waiting before draining stderr would hang.
                    System.err.print("warning".repeat(20000));
                }
                case "failure" -> {
                    System.out.print("incomplete dump");
                    System.exit(2);
                }
                case "sleep" -> Thread.sleep(60000);
                case "empty" -> { }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }

    private ProcessBuilder process(String mode) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(DumpProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return new ProcessBuilder(java, "-cp", classes, DumpProcess.class.getName(), mode);
    }

    private List<String> files() throws Exception {
        try (var stream = Files.list(directory)) {
            return stream.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void failureWithPartialOutputPreservesOldBackupAndRemovesTemporaryFiles() throws Exception {
        Path old = Files.writeString(directory.resolve("20200101000000.bak"), "old backup");
        assertThrows(BaseException.class, () -> MysqlBackupUtil.backup(directory, 1, process("failure")));
        assertEquals("old backup", Files.readString(old));
        assertEquals(List.of("20200101000000.bak"), files());
    }

    @Test
    void zeroExitCodeWithEmptyOutputIsStillFailure() {
        assertThrows(BaseException.class, () -> MysqlBackupUtil.backup(directory, 1, process("empty")));
    }

    @Test
    void successfulBackupPrunesOnlyBackupFilesAndHandlesLargeStderr() throws Exception {
        Files.writeString(directory.resolve("20200101000000.bak"), "old backup");
        Files.writeString(directory.resolve("notes.txt"), "keep");
        Files.writeString(directory.resolve("manual.bak"), "keep");
        Files.createDirectory(directory.resolve("20200102000000.bak"));
        Path completed = Path.of(MysqlBackupUtil.backup(directory, 1, process("success")));
        assertEquals("CREATE TABLE example(id INT);", Files.readString(completed));
        assertFalse(Files.exists(directory.resolve("20200101000000.bak")));
        assertEquals(4, files().size());
        assertTrue(Files.exists(directory.resolve("notes.txt")));
        assertTrue(Files.exists(directory.resolve("manual.bak")));
        assertTrue(Files.isDirectory(directory.resolve("20200102000000.bak")));
    }

    @Test
    void interruptRestoresFlagAndCleansTemporaryFiles() throws Exception {
        try {
            Thread.currentThread().interrupt();
            assertThrows(BaseException.class, () -> MysqlBackupUtil.backup(directory, 1, process("sleep")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertTrue(files().isEmpty());
    }

    @Test
    void concurrentBackupsKeepOneCompleteFile() throws Exception {
        // 同时启动多个任务，验证发布和清理不会互删所有备份。
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Future<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    return MysqlBackupUtil.backup(directory, 1, process("success"));
                }));
            }
            start.countDown();
            for (Future<String> task : tasks) {
                task.get(20, TimeUnit.SECONDS);
            }
        }
        try (var entries = Files.list(directory)) {
            List<Path> backups = entries.filter(p -> p.toString().endsWith(".bak")).toList();
            assertEquals(1, backups.size());
            assertEquals("CREATE TABLE example(id INT);", Files.readString(backups.getFirst()));
        }
        assertEquals(1, files().size()); // 只保留一个正式备份。
    }

    @Test
    void streamBackupIsIsolatedAndCleansUpAfterConsumer() throws Exception {
        ProcessBuilder builder = process("success");
        AtomicReference<Path> temporary = new AtomicReference<>();
        MysqlBackupUtil.backupStream(builder, stream -> {
            Path file = builder.redirectOutput().file().toPath();
            temporary.set(file);
            assertNotEquals(directory, file.getParent());
            assertTrue(Files.exists(file));
            assertDoesNotThrow(() -> assertEquals("CREATE TABLE example(id INT);",
                    new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            // 流消费期间执行磁盘备份和清理，不应影响流文件。
            assertDoesNotThrow(() -> MysqlBackupUtil.backup(directory, 1, process("success")));
            assertTrue(Files.exists(file));
        });
        assertFalse(Files.exists(temporary.get().getParent()));
        assertEquals(1, files().size());
    }

    @Test
    void streamConsumerFailureClosesStreamAndRemovesTemporaryDirectory() throws Exception {
        ProcessBuilder builder = process("success");
        IllegalStateException failure = new IllegalStateException("consumer failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> MysqlBackupUtil.backupStream(builder, stream -> { throw failure; })));
        assertFalse(Files.exists(builder.redirectOutput().file().toPath().getParent()));
    }

    @Test
    void streamDumpFailureCleansDirectoryWithoutCallingConsumer() throws Exception {
        ProcessBuilder builder = process("failure");
        assertThrows(BaseException.class, () -> MysqlBackupUtil.backupStream(builder,
                stream -> fail("Consumer must not receive an incomplete dump")));
        assertFalse(Files.exists(builder.redirectOutput().file().toPath().getParent()));
    }

    @Test
    void commandPassesPasswordAsOneArgument() {
        // 空格、引号及 shell 特殊字符应原样保留在同一个密码参数内。
        String password = "p a ss\";&$";
        ProcessBuilder builder = MysqlBackupUtil.dumpProcess("localhost", 3306, "test user", password, "test db");
        assertEquals(List.of("mysqldump",
                "--host=localhost", "--port=3306", "--user=test user", "--password=" + password,
                "--single-transaction", "--skip-lock-tables", "--quick",
                "--routines", "--events", "--triggers", "--databases", "--", "test db"), builder.command());
    }
}
