package cn.bcd.lib.spring.database.jdbc.backup.mysql;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.*;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MysqlBackupUtil {

    static Logger logger = LoggerFactory.getLogger(MysqlBackupUtil.class);

    /**
     * 备份mysql数据库到指定目录
     * 服务器必须安装mysqldump命令
     *
     * @param host       mysql地址
     * @param port       mysql端口
     * @param username   mysql用户名
     * @param password   mysql密码
     * @param database   备份数据库名
     * @param dir        备份文件保存文件夹
     * @param maxFileNum 最大备份文件数量、0则表示不限制
     * @return 文件地址
     */
    public static String backup_disk(String host,
                                     int port,
                                     String username,
                                     String password,
                                     String database,
                                     String dir,
                                     int maxFileNum) {
        Path root = Paths.get(dir).toAbsolutePath().normalize();
        Path databaseDir = root.resolve(database).normalize();
        if (database.isBlank() || !root.equals(databaseDir.getParent())) {
            throw BaseException.get("database must be a directory name");
        }
        Path credentials = null;
        try {
            credentials = createCredentials(password);
            return backup(databaseDir, maxFileNum, dumpProcess(host, port, username, credentials, database));
        } catch (IOException ex) {
            throw BaseException.get(ex);
        } finally {
            deleteTemporaryFile(credentials);
        }
    }

    static ProcessBuilder dumpProcess(String host, int port, String username, Path credentials, String database) {
        return new ProcessBuilder("mysqldump", "--defaults-extra-file=" + credentials.toAbsolutePath(),
                "--host=" + host, "--port=" + port, "--user=" + username, "--databases", "--", database);
    }

    static Path createCredentials(String password) throws IOException {
        Path path = Files.createTempFile("mysql-backup-", ".cnf");
        try {
            PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
            if (posix != null) {
                posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            } else {
                AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
                if (acl == null) {
                    throw new IOException("cannot restrict backup credential file permissions");
                }
                acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                        .setPrincipal(Files.getOwner(path))
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            }
            String escaped = password.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                    .replace("\b", "\\b");
            Files.writeString(path, "[client]\npassword=\"" + escaped + "\"\n");
            return path;
        } catch (IOException | RuntimeException ex) {
            deleteTemporaryFile(path);
            throw ex;
        }
    }

    static String backup(Path directory, int maxFileNum, ProcessBuilder builder) {
        Path partial = null;
        Path errors = null;
        Process process = null;
        try {
            Files.createDirectories(directory);
            String prefix = DateUtil.dateToStr_yyyyMMddHHmmss(new Date()) + "-";
            partial = Files.createTempFile(directory, prefix, ".part");
            errors = Files.createTempFile(directory, prefix, ".err");
            // Redirect both streams before waiting, so neither pipe can fill up.
            process = builder.redirectOutput(partial.toFile()).redirectError(errors.toFile()).start();
            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                throw BaseException.get("database backup timed out");
            }
            if (process.exitValue() != 0 || Files.size(partial) == 0) {
                // Do not log arbitrary process output, which may contain credentials.
                throw BaseException.get("database backup failed, exitCode[{}]", process.exitValue());
            }
            String name = partial.getFileName().toString().replace(".part", ".bak");
            Path completed = directory.resolve(name);
            Files.move(partial, completed);
            partial = null;
            pruneBackups(directory, completed, maxFileNum);
            logger.info("database backup complete: {}", completed);
            return completed.toString();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BaseException.get(ex);
        } catch (IOException ex) {
            throw BaseException.get(ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                // Wait for file handles to close even when the caller was interrupted.
                boolean interrupted = Thread.interrupted();
                try {
                    while (process.isAlive()) {
                        try {
                            process.waitFor();
                        } catch (InterruptedException ex) {
                            interrupted = true;
                        }
                    }
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            deleteTemporaryFile(partial);
            deleteTemporaryFile(errors);
        }
    }

    private static void pruneBackups(Path directory, Path completed, int maxFileNum) throws IOException {
        if (maxFileNum <= 0) {
            return;
        }
        List<Path> previous;
        try (Stream<Path> stream = Files.list(directory)) {
            previous = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("\\d{14}(-\\d+)?\\.bak"))
                    .filter(path -> !path.equals(completed))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
        }
        // The just-completed backup always occupies one retention slot.
        for (int i = maxFileNum - 1; i < previous.size(); i++) {
            Files.deleteIfExists(previous.get(i));
        }
    }

    private static void deleteTemporaryFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                logger.warn("cannot remove temporary backup file: {}", path, ex);
            }
        }
    }

    public static void backup_stream(String host,
                                     int port,
                                     String username,
                                     String password,
                                     String database,
                                     Consumer<InputStream> consumer) {
        String filePath = backup_disk(host, port, username, password, database, "temp", 0);
        Path p = Paths.get(filePath);
        try (InputStream is = Files.newInputStream(p)) {
            consumer.accept(is);
        } catch (IOException e) {
            throw BaseException.get(e);
        } finally {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                logger.error("error", e);
            }
        }
    }
}
