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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MysqlBackupUtil {

    private static final Logger logger = LoggerFactory.getLogger(MysqlBackupUtil.class);

    /**
     * 备份mysql数据库到指定目录
     * 服务器必须安装mysqldump命令。
     * 使用事务快照，要求业务表采用 InnoDB，备份期间应避免执行 DDL。
     * 发布及清理在当前 JVM 内串行执行。
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
        return backup(databaseDir, maxFileNum, dumpProcess(host, port, username, password, database));
    }

    static ProcessBuilder dumpProcess(String host, int port, String username, String password, String database) {
        // 每个参数单独传给进程，密码中的空格等字符不经过 shell 解析。
        // 事务快照避免长时间锁表，并包含存储过程、函数、事件和触发器。
        return new ProcessBuilder("mysqldump",
                "--host=" + host, "--port=" + port, "--user=" + username, "--password=" + password,
                "--single-transaction", "--skip-lock-tables", "--quick",
                "--routines", "--events", "--triggers", "--databases", "--", database);
    }

    static String backup(Path directory, int maxFileNum, ProcessBuilder builder) {
        Path partial = dump(directory, builder);
        try {
            Path completed = publish(directory, partial, maxFileNum);
            logger.info("database backup complete: {}", completed);
            return completed.toString();
        } catch (IOException ex) {
            throw BaseException.get(ex);
        } finally {
            deleteTemporaryFile(partial);
        }
    }

    /** 导出成功且输出非空后，将临时文件的清理责任交给调用方。 */
    private static Path dump(Path directory, ProcessBuilder builder) {
        Path partial = null;
        Path errors = null;
        Process process = null;
        try {
            Files.createDirectories(directory);
            String prefix = DateUtil.dateToStr_yyyyMMddHHmmss(new Date()) + "-";
            partial = Files.createTempFile(directory, prefix, ".part");
            errors = Files.createTempFile(directory, prefix, ".err");
            // 直接重定向两个输出流，避免等待进程时管道缓冲区填满而死锁。
            builder.redirectOutput(partial.toFile()).redirectError(errors.toFile());
            // 按配置打印完整命令，包含明文密码，便于核对实际执行参数。
            logger.info("execute database backup command: {}", String.join(" ", builder.command()));
            process = builder.start();
            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                throw BaseException.get("database backup timed out");
            }
            if (process.exitValue() != 0 || Files.size(partial) == 0) {
                // 进程输出可能包含敏感信息，不直接写入日志。
                throw BaseException.get("database backup failed, exitCode[{}]", process.exitValue());
            }
            Path result = partial;
            partial = null;
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BaseException.get(ex);
        } catch (IOException ex) {
            throw BaseException.get(ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                // 即使线程已中断，也要等待文件句柄关闭后再清理临时文件。
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

    /**
     * 发布和保留清理处于同一临界区，防止并发任务互删新备份。
     * synchronized 协调当前 JVM 内的并发调用，数据库导出不持有此锁。
     */
    private static synchronized Path publish(Path directory, Path partial, int maxFileNum) throws IOException {
        String name = partial.getFileName().toString().replace(".part", ".bak");
        Path completed = directory.resolve(name);
        Files.move(partial, completed);
        try {
            pruneBackups(directory, completed, maxFileNum);
        } catch (IOException ex) {
            // 新备份已成功落盘；清理失败只告警，允许下次任务重试。
            logger.warn("cannot prune old backups: {}", directory, ex);
        }
        return completed;
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
        // 本次完成的备份始终占一个保留名额。
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

    /**
     * 导出完成后同步消费 SQL 流，回调返回时关闭流并清理文件。
     * 每次使用独立临时目录，与磁盘备份的保留清理隔离。
     */
    public static void backup_stream(String host,
                                     int port,
                                     String username,
                                     String password,
                                     String database,
                                     Consumer<InputStream> consumer) {
        backupStream(dumpProcess(host, port, username, password, database), consumer);
    }

    static void backupStream(ProcessBuilder builder, Consumer<InputStream> consumer) {
        Path directory = null;
        Path file = null;
        try {
            directory = Files.createTempDirectory("mysql-backup-stream-");
            file = dump(directory, builder);
            try (InputStream stream = Files.newInputStream(file)) {
                consumer.accept(stream);
            }
        } catch (IOException ex) {
            throw BaseException.get(ex);
        } finally {
            // 回调抛出异常时同样清理；先关闭流，再删除文件及独立目录。
            deleteTemporaryFile(file);
            deleteTemporaryFile(directory);
        }
    }
}
