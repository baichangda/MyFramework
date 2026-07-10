package cn.bcd.lib.parser.base.util;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.CompileUtil;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DynamicProcessorCompiler {
    private DynamicProcessorCompiler() {
    }

    public static Class<?> compileAndDefine(String className, String source, boolean generateClassFile) {
        try {
            source = normalizeBinaryNestedClassNames(source);
            if (generateClassFile) {
                return CompileUtil.compile(className, source, Path.of("src/main/java"));
            }
            return CompileUtil.compileInMemory(className, source);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    private static String normalizeBinaryNestedClassNames(String source) {
        Pattern pattern = Pattern.compile("([a-zA-Z_$][\\w$]*\\.)+[a-zA-Z_$][\\w$]*(\\$[a-zA-Z_$][\\w$]*)+");
        Matcher matcher = pattern.matcher(source);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String binaryName = matcher.group();
            String replacement = binaryName;
            try {
                Class<?> clazz = Class.forName(binaryName);
                if (clazz.getCanonicalName() != null) {
                    replacement = clazz.getCanonicalName();
                }
            } catch (ClassNotFoundException ignored) {
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

}
