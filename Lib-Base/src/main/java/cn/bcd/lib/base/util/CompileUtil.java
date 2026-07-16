package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileUtil {
    private static final Pattern BINARY_NESTED_CLASS_NAME = Pattern.compile(
            "([a-zA-Z_$][\\w$]*\\.)+[a-zA-Z_$][\\w$]*(\\$[a-zA-Z_$][\\w$]*)+");

    public static String normalizeBinaryNestedClassNames(String source) {
        Matcher matcher = BINARY_NESTED_CLASS_NAME.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String binaryName = matcher.group();
            String replacement = binaryName;
            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) {
                    classLoader = CompileUtil.class.getClassLoader();
                }
                Class<?> clazz = Class.forName(binaryName, false, classLoader);
                if (clazz.getCanonicalName() != null) {
                    replacement = clazz.getCanonicalName();
                }
            } catch (ClassNotFoundException ignored) {
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static Class<?> compileInMemory(String className, String sourceCode) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw BaseException.get("no java compiler found、must be jdk not jre");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, null);
             MemoryJavaFileManager fileManager = new MemoryJavaFileManager(standardFileManager)) {
            List<String> options = List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-proc:none"
            );
            JavaFileObject sourceFile = new StringJavaFileObject(className, sourceCode);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            if (!task.call()) {
                throw BaseException.get(formatCompileError(className, sourceCode, diagnostics));
            }
            byte[] bytes = fileManager.getClassBytes(className);
            if (bytes == null) {
                throw BaseException.get("compile failed（类名：" + className + "）：no class bytes generated");
            }
            return new MemoryClassLoader(fileManager.getAllClassBytes()).loadClass(className);
        } catch (IOException | ClassNotFoundException e) {
            throw BaseException.get(e);
        }
    }


    public static Class<?> compile(String className, Path sourceCodePath, Path outputDirPath) throws Exception {
        return compile(className, Files.readString(sourceCodePath), outputDirPath);
    }

    public static Class<?> compile(String className, String sourceCode, Path outputDirPath) throws Exception {
        //获取系统编译器实例
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw BaseException.get("no java compiler found、must be jdk not jre");
        }

        //创建诊断收集器（用于捕获编译错误）
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        //创建文件管理器（指定编译输出目录）
        boolean success;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            //输出目录（例如当前目录下的classes文件夹）
            Files.createDirectories(outputDirPath);
            // 设置编译输出目录
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDirPath.toFile()));
            //添加classpath，包含当前线程的类路径（解决依赖问题）
            List<String> options = List.of(
                    "-classpath",
                    System.getProperty("java.class.path") + File.pathSeparator + outputDirPath.toAbsolutePath()
            );

            //创建编译任务
            JavaFileObject sourceFile = new StringJavaFileObject(className, sourceCode); // 字符串源码对象
            Iterable<? extends JavaFileObject> compilationUnits = List.of(sourceFile);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,           // 输出流（null表示默认）
                    fileManager,        // 文件管理器
                    diagnostics,        // 诊断收集器
                    options,            // 编译选项
                    null,               // 要处理的注解类
                    compilationUnits    // 待编译的源码
            );
            //执行编译任务
            success = task.call();
        }
        if (!success) {
            // 抛出包含详细信息的异常
            throw BaseException.get(formatCompileError(className, sourceCode, diagnostics));
        }

        //加载类
        CustomClassLoader classLoader = new CustomClassLoader(outputDirPath.toAbsolutePath().toString());
        return classLoader.loadClass(className);
    }

    private static String formatCompileError(String className, String sourceCode, DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder errorMsg = new StringBuilder("compile failed（类名：" + className + "）：\n");
        String[] sourceLines = sourceCode.split("\\R", -1);
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            // 错误类型（ERROR/WARNING/NOTE）
            String kind = diagnostic.getKind().name();
            // 行号（-1 表示无行号信息）
            long line = diagnostic.getLineNumber();
            // 列号（-1 表示无列号信息）
            long column = diagnostic.getColumnNumber();
            // 错误描述
            String msg = diagnostic.getMessage(null);
            // 拼接格式化的错误信息
            errorMsg.append("[").append(kind).append("]")
                    .append(line != -1 ? " 行：" + line : "")
                    .append(column != -1 ? " 列：" + column : "")
                    .append("：").append(msg)
                    .append("\n");
            appendDiagnosticSourceLine(errorMsg, sourceLines, line, column);
        }
        return errorMsg.toString();
    }

    private static void appendDiagnosticSourceLine(StringBuilder errorMsg, String[] sourceLines, long line, long column) {
        if (line < 1 || line > sourceLines.length) {
            return;
        }
        int errorLineIndex = (int) line - 1;
        int startLineIndex = Math.max(0, errorLineIndex - 2);
        int endLineIndex = Math.min(sourceLines.length - 1, errorLineIndex + 2);
        int lineNoWidth = String.valueOf(endLineIndex + 1).length();
        for (int i = startLineIndex; i <= endLineIndex; i++) {
            int lineNo = i + 1;
            String sourceLine = sourceLines[i];
            String prefix = String.format("    %" + lineNoWidth + "d | ", lineNo);
            errorMsg.append(prefix).append(sourceLine).append("\n");
            if (i == errorLineIndex && column >= 1) {
                int pointerColumn = Math.max(1, Math.min((int) column, sourceLine.length() + 1));
                errorMsg.append(" ".repeat(prefix.length() + pointerColumn - 1)).append("^").append("\n");
            }
        }
    }


    static class CustomClassLoader extends ClassLoader {
        private final String classDir; // class文件所在目录

        public CustomClassLoader(String classDir) {
            this.classDir = classDir;
        }

        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
            Path classFilePath = Paths.get(classDir)
                    .resolve(className.replace('.', File.separatorChar) + ".class");
            try {
                if (!Files.exists(classFilePath)) {
                    throw new ClassNotFoundException("classFile[" + classFilePath + "] not exist");
                }
                // 直接读取字节数组
                byte[] classBytes = Files.readAllBytes(classFilePath);
                return defineClass(className, classBytes, 0, classBytes.length);
            } catch (IOException e) {
                throw BaseException.get("load classFile[{}] error", classFilePath, e);
            }
        }
    }

    static class StringJavaFileObject extends SimpleJavaFileObject {
        private final String code; // 字符串形式的源码

        // 构造方法：指定类名（URI格式）和源码类型（SOURCE）
        public StringJavaFileObject(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        // 返回字符串源码（编译器会读取此内容）
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }


    static class MemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> className_bytes;

        public MemoryClassLoader(Map<String, byte[]> className_bytes) {
            this.className_bytes = className_bytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = className_bytes.get(name);
            if (bytes == null) {
                return super.findClass(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteCodeJavaFileObject> className_fileObject = new ConcurrentHashMap<>();

        public MemoryJavaFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            ByteCodeJavaFileObject fileObject = new ByteCodeJavaFileObject(className, kind);
            className_fileObject.put(className, fileObject);
            return fileObject;
        }

        public byte[] getClassBytes(String className) {
            ByteCodeJavaFileObject fileObject = className_fileObject.get(className);
            return fileObject == null ? null : fileObject.bytes();
        }

        public Map<String, byte[]> getAllClassBytes() {
            Map<String, byte[]> res = new ConcurrentHashMap<>();
            for (Map.Entry<String, ByteCodeJavaFileObject> entry : className_fileObject.entrySet()) {
                res.put(entry.getKey(), entry.getValue().bytes());
            }
            return res;
        }
    }

    static class ByteCodeJavaFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        public ByteCodeJavaFileObject(String className, Kind kind) {
            super(URI.create("bytes:///" + className.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        public byte[] bytes() {
            return outputStream.toByteArray();
        }
    }


    public static void main(String[] args) {
        try {
            String sourceCode1 = """
                    package cn.bcd;
                    public class Test1 {
                        private String message;
                        public Test1(String msg) {
                            this.message = msg;
                        }
                        public void test() {
                            System.out.println(message);
                        }
                    }
                    """;
            // 编译源码，获取class文件目录
            Class<?> clazz1 = compile("cn.bcd.Test1", sourceCode1, Paths.get("classes"));
            // 反射实例化并调用方法
            Object instance1 = clazz1.getConstructor(String.class).newInstance("Hello,Test1");
            clazz1.getMethod("test").invoke(instance1);


            String sourceCode2 = """
                            package cn.bcd.test;
                            import java.util.Date;
                            public class Test2 {
                                public Test2() {
                                }
                                public Date now() {
                                    return new Date();
                                }
                            }
                    """;
            Class<?> clazz2 = compile("cn.bcd.test.Test2", sourceCode2, Paths.get("classes"));
            Object instance2 = clazz2.getConstructor().newInstance();
            Date now = (Date) clazz2.getMethod("now").invoke(instance2);
            System.out.println(now);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
