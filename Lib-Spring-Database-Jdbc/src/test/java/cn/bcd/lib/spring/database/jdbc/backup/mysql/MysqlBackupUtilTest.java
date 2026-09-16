package cn.bcd.lib.spring.database.jdbc.backup.mysql;

import cn.bcd.lib.base.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    void credentialFileEscapesQuotesBackslashesAndLineBreaks() throws Exception {
        Path credentials = MysqlBackupUtil.createCredentials("a\"b\\c\n\r\t\b");
        try {
            assertEquals("[client]\npassword=\"a\\\"b\\\\c\\n\\r\\t\\b\"\n", Files.readString(credentials));
            var posix = Files.getFileAttributeView(credentials, java.nio.file.attribute.PosixFileAttributeView.class);
            if (posix != null) {
                assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                        posix.readAttributes().permissions());
            } else {
                var acl = Files.getFileAttributeView(credentials, java.nio.file.attribute.AclFileAttributeView.class);
                assertEquals(1, acl.getAcl().size());
                assertEquals(Files.getOwner(credentials), acl.getAcl().getFirst().principal());
            }
        } finally {
            Files.deleteIfExists(credentials);
        }
    }

    @Test
    void commandSeparatesArgumentsAndDoesNotContainPassword() throws Exception {
        String password = "p a ss;&$";
        Path credentials = MysqlBackupUtil.createCredentials(password);
        try {
            ProcessBuilder builder = MysqlBackupUtil.dumpProcess("localhost", 3306, "test user", credentials, "test db");
            assertEquals(List.of("mysqldump", "--defaults-extra-file=" + credentials.toAbsolutePath(),
                    "--host=localhost", "--port=3306", "--user=test user", "--databases", "--", "test db"), builder.command());
            assertFalse(builder.command().toString().contains(password));
            assertTrue(Files.readString(credentials).contains(password));
        } finally {
            Files.deleteIfExists(credentials);
        }
    }
}
