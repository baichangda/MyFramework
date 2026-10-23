package cn.bcd.lib.base.util;


import cn.bcd.lib.base.exception.BaseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文件遍历与删除工具。
 * 对目录符号链接和 Windows 目录联接只处理链接本身，不遍历其目标目录。
 */
public class FileUtil {
    /**
     * 删除文件，或递归删除目录及其内容；不存在的路径直接跳过。
     * 遇到目录符号链接或 Windows 目录联接时，仅删除链接本身，保留目标目录及其内容。
     * 多个路径按传入顺序处理，发生异常时停止，已完成的删除不会回滚。
     *
     * @param dirs 待删除的文件或目录路径
     * @throws RuntimeException 读取目录或删除文件失败时抛出，原始异常作为原因保留
     */
    public static void deleteDirRecursion(String... dirs) {
        try {
            for (String dir : dirs) {
                Path root = Paths.get(dir);
                // 不跟随链接判断是否存在，确保目标已丢失的链接仍可被删除。
                if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    /**
                     * 进入目录前识别目录联接，删除联接后跳过其目标子树。
                     */
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                            throws IOException {
                        // Windows 目录联接的属性为目录且 isOther 为真，不能只用 isSymbolicLink 判断。
                        if (attrs.isOther()) {
                            Files.deleteIfExists(directory);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    /**
                     * 无法打开失效的目录联接时，仍尝试删除联接本身；其他访问错误继续抛出。
                     */
                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                        // 不跟随链接读取属性，避免因联接目标不存在而无法识别联接。
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class,
                                LinkOption.NOFOLLOW_LINKS);
                        if (attrs.isDirectory() && attrs.isOther()) {
                            Files.deleteIfExists(file);
                            return FileVisitResult.CONTINUE;
                        }
                        throw error;
                    }

                    /**
                     * 删除普通文件或符号链接，不跟随符号链接删除目标。
                     */
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    /**
                     * 子项处理完成后删除空目录；遍历异常时保留异常并停止删除。
                     */
                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                        if (error != null) {
                            throw error;
                        }
                        Files.deleteIfExists(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 列出指定目录中的文件，可选择递归遍历普通子目录，返回结果不包含目录。
     * 不遍历目录符号链接或 Windows 目录联接，传入的根路径为此类链接时也会跳过。
     * 普通文件路径和不存在的根路径直接跳过；文件符号链接保留为链接路径。
     * 多个根目录之间不去重，返回顺序取决于文件系统的枚举顺序。
     *
     * @param recursion 是否递归遍历普通子目录
     * @param dirs 待遍历的目录路径
     * @return 收集到的文件路径列表
     * @throws BaseException 读取目录或文件属性失败时抛出
     */
    public static List<Path> listDir(boolean recursion, String... dirs) {
        List<Path> fileList = new ArrayList<>();
        // 按索引处理待遍历目录，递归时将新发现的普通子目录追加到末尾。
        List<Path> dirList = new ArrayList<>(Arrays.stream(dirs).map(Paths::get).toList());
        try {
            for (int i = 0; i < dirList.size(); i++) {
                Path dir = dirList.get(i);
                // 根路径同样需要排除目录符号链接和 Windows 目录联接。
                if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)
                        && !Files.readAttributes(dir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()) {
                    try (final Stream<Path> stream = Files.list(dir)) {
                        List<Path> collect = stream.toList();
                        for (Path p : collect) {
                            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class,
                                    LinkOption.NOFOLLOW_LINKS);
                            // 使用链接自身的属性分类，避免进入联接目标或产生循环遍历。
                            if (attrs.isDirectory()) {
                                if (recursion && !attrs.isOther()) {
                                    dirList.add(p);
                                }
                            } else if (!attrs.isSymbolicLink() || !Files.isDirectory(p)) {
                                // 排除指向目录的符号链接，保留普通文件及其他非目录项。
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
