package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

public class CompileUtil {

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
            StringBuilder errorMsg = new StringBuilder("compile failed（类名：" + className + "）：\n");
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
            }
            // 抛出包含详细信息的异常
            throw BaseException.get(errorMsg.toString());
        }

        //加载类
        CustomClassLoader classLoader = new CustomClassLoader(outputDirPath.toAbsolutePath().toString());
        return classLoader.loadClass(className);
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
                            public class Test2 {
                                private String message;
                                public Test2(String msg) {
                                    this.message = msg;
                                }
                                public void test() {
                                    System.out.println(message);
                                }
                            }
                    """;
            Files.write(Paths.get("tempSourceCode.txt"), sourceCode2.getBytes());
            Class<?> clazz2 = compile("cn.bcd.test.Test2", Paths.get("tempSourceCode.txt"), Paths.get("classes"));
            Object instance2 = clazz2.getConstructor(String.class).newInstance("Hello,Test2");
            clazz2.getMethod("test").invoke(instance2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
