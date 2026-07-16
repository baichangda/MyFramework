package cn.bcd.lib.parser.base.complier;

/**
 * Compiles the generated source of a dynamic processor and defines its class.
 */
public interface DynamicProcessorCompiler {

    /**
     * Compiles with the currently selected strategy. Javassist is used by default.
     */
    static Class<?> compileAndDefine(String className, String source, boolean generateClassFile) {
        return CompilerHolder.compiler.compile(className, source, generateClassFile);
    }

    /**
     * Changes the strategy used by generated processors.
     */
    static void setCompiler(DynamicProcessorCompiler compiler) {
        CompilerHolder.compiler = java.util.Objects.requireNonNull(compiler, "compiler");
    }

    /**
     * @param className fully qualified processor class name
     * @param source generated Java source
     * @param generateClassFile whether to write the compiled class to {@code src/main/java}
     * @return the defined processor class
     */
    Class<?> compile(String className, String source, boolean generateClassFile);

    final class CompilerHolder {
        private static volatile DynamicProcessorCompiler compiler = new JavassistDynamicProcessorCompiler();

        private CompilerHolder() {
        }
    }
}
