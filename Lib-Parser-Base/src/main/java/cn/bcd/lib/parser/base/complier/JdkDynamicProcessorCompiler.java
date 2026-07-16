package cn.bcd.lib.parser.base.complier;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.CompileUtil;

import java.nio.file.Path;

/** Uses the JDK compiler to compile the complete generated Java source. */
public final class JdkDynamicProcessorCompiler implements DynamicProcessorCompiler {
    private static final Path CLASS_OUTPUT_PATH = Path.of("src/main/java");

    @Override
    public Class<?> compile(String className, String source, boolean generateClassFile) {
        try {
            source = CompileUtil.normalizeBinaryNestedClassNames(source);
            if (generateClassFile) {
                return CompileUtil.compile(className, source, CLASS_OUTPUT_PATH);
            }
            return CompileUtil.compileInMemory(className, source);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }
}
