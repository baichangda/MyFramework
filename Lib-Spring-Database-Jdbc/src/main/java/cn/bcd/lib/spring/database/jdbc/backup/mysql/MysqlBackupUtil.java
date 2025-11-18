package cn.bcd.lib.spring.database.jdbc.backup.mysql;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateZoneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
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
        String databaseDir = dir + File.separator + database;
        try {
            Files.createDirectories(Paths.get(databaseDir));
        } catch (IOException e) {
            throw BaseException.get(e);
        }
        String fileName = DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date()) + ".bak";
        String filePath = databaseDir + File.separator + fileName;
        logger.info("start backup database[{}] to path[{}]", database, filePath);
        String cmd = "mysqldump -h" + host + " -P" + port + " -u" + username + " -p" + password + " --databases " + database + " > " + filePath;
        String[] command = {"/bin/bash", "-c", cmd};
        logger.info("execute backup[{}]", cmd);
        Path p = Paths.get(filePath);
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            try (InputStream is = process.getErrorStream()) {
                int len = is.available();
                if (len > 0) {
                    byte[] result = new byte[len];
                    int readRes = is.read(result);
                    logger.error("database[{}] backup error read[{}] result:\n{}", database, readRes, new String(result));
                }
            }
            if (Files.size(p) == 0) {
                throw BaseException.get("database[{}] backup failed,can't find backup file[{}]", database, filePath);
            }

            if (maxFileNum > 0) {
                try (Stream<Path> stream = Files.list(Paths.get(dir))) {
                    List<Path> fileList = new ArrayList<>(stream.toList());
                    if (fileList.size() > maxFileNum) {
                        fileList.sort(Comparator.comparing(Path::getFileName));
                        for (int i = 0; i < fileList.size() - maxFileNum; i++) {
                            Path curFile = fileList.get(i);
                            Files.deleteIfExists(curFile);
                            logger.info("delete backup file[{}]", curFile);
                        }
                    }
                }
            }
            return filePath;
        } catch (IOException | InterruptedException ex) {
            throw BaseException.get(ex);
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
