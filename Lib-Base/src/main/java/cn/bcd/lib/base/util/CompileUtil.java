package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        //输出目录（例如当前目录下的classes文件夹）
        Files.createDirectories(outputDirPath);
        // 设置编译输出目录
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDirPath.toFile()));

        //创建编译任务
        JavaFileObject sourceFile = new StringJavaFileObject(className, sourceCode); // 字符串源码对象
        Iterable<? extends JavaFileObject> compilationUnits = List.of(sourceFile);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,               // 输出流（null表示默认）
                fileManager,        // 文件管理器
                diagnostics,        // 诊断收集器
                null,               // 编译选项（例如 -classpath）
                null,               // 要处理的注解类
                compilationUnits    // 待编译的源码
        );

        //执行编译任务
        boolean success = task.call();
        fileManager.close();

        //检查编译是否成功（失败则打印错误）
        if (!success) {
            StringBuilder errorMsg = new StringBuilder("compile failed：");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorMsg.append("\n").append(diagnostic.getMessage(null));
            }
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

        //重写findClass方法，加载指定类
        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
            try {
                //拼接class文件路径（例如 ./classes/DynamicClass.class）
                String classFilePath = classDir + File.separator + className.replace('.', File.separatorChar) + ".class";
                File classFile = new File(classFilePath);
                if (!classFile.exists()) {
                    throw new ClassNotFoundException("class文件不存在：" + classFilePath);
                }

                //读取class文件字节流
                FileInputStream fis = new FileInputStream(classFile);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }
                byte[] classBytes = bos.toByteArray();
                fis.close();
                bos.close();

                //定义类（将字节流转换为Class对象）
                return defineClass(className, classBytes, 0, classBytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException("加载class文件失败", e);
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
            // 编译源码，获取class文件目录
            Class<?> clazz1 = compile("DynamicClass", """
                    public class DynamicClass {
                        private String message;
                        public DynamicClass(String msg) {
                            this.message = msg;
                        }
                        public void print() {
                            System.out.println("动态类输出：" + message);
                        }
                    }
                    """, Paths.get("classes"));
            // 反射实例化并调用方法
            Object instance = clazz1.getConstructor(String.class).newInstance("Hello, Dynamic!");
            clazz1.getMethod("print").invoke(instance); // 输出：动态类输出：Hello, Dynamic!
            Class<?> clazz2 = compile("cn.bcd.lib.base.util.DateUtilTest", Paths.get("d:/test.txt"), Paths.get("classes"));
            Date res2 = (Date) clazz2.getMethod("clearMillis", Date.class).invoke(null, new Date());
            System.out.println(res2.getTime());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
