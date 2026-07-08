package cn.bcd.lib.base.util;


import cn.bcd.lib.base.exception.BaseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class FileUtil {
    /**
     * 删除文件
     * 或者递归删除文件夹
     *
     * @param dirs
     */
    public static void deleteDirRecursion(String... dirs) {
        List<Path> fileList = listDir(true, dirs);
        try {
            for (Path path : fileList) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Path> listDir(boolean recursion, String... dirs) {
        List<Path> fileList = new ArrayList<>();
        List<Path> dirList = Arrays.stream(dirs).map(Paths::get).toList();
        try {
            for (int i = 0; i < dirList.size(); i++) {
                Path dir = dirList.get(i);
                if (Files.isDirectory(dir)) {
                    try (final Stream<Path> stream = Files.list(dir)) {
                        List<Path> collect = stream.toList();
                        for (Path p : collect) {
                            if (Files.isDirectory(p)) {
                                if (recursion) {
                                    dirList.add(p);
                                }
                            } else {
                                fileList.add(p);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw BaseException.get(ex);
        }
        return fileList;
    }

}
