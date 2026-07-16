package cn.bcd.lib.parser.base.complier;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.CompileUtil;
import cn.bcd.lib.parser.base.processor.Processor;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;

import java.nio.file.Files;
import java.nio.file.Path;

/** Uses Javassist to compile the members of the generated processor source. */
public final class JavassistDynamicProcessorCompiler implements DynamicProcessorCompiler {
    private static final Path CLASS_OUTPUT_PATH = Path.of("src/main/java");

    @Override
    public Class<?> compile(String className, String source, boolean generateClassFile) {
        CtClass ctClass = null;
        try {
            source = CompileUtil.normalizeBinaryNestedClassNames(source);
            SourceParts sourceParts = parseSource(className, source);

            ClassPool classPool = new ClassPool(true);
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                classPool.appendClassPath(new LoaderClassPath(contextClassLoader));
            }

            ctClass = classPool.makeClass(className);
            ctClass.addInterface(classPool.get(Processor.class.getName()));
            addFields(ctClass, sourceParts.fields());

            CtConstructor constructor = CtNewConstructor.make(sanitizeMemberSource(sourceParts.constructor()), ctClass);
            ctClass.addConstructor(constructor);
            ctClass.addMethod(CtNewMethod.make(sanitizeMemberSource(sourceParts.processMethod()), ctClass));
            ctClass.addMethod(CtNewMethod.make(sanitizeMemberSource(sourceParts.deProcessMethod()), ctClass));

            if (generateClassFile) {
                Files.createDirectories(CLASS_OUTPUT_PATH);
                ctClass.writeFile(CLASS_OUTPUT_PATH.toString());
            }
            return ctClass.toClass(Processor.class);
        } catch (Exception e) {
            throw BaseException.get(e);
        } finally {
            if (ctClass != null) {
                ctClass.detach();
            }
        }
    }

    private static void addFields(CtClass ctClass, String fields) throws Exception {
        int start = 0;
        int depth = 0;
        for (int i = 0; i < fields.length(); i++) {
            char c = fields.charAt(i);
            if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                depth--;
            } else if (c == ';' && depth == 0) {
                String field = fields.substring(start, i + 1).trim();
                if (!field.isEmpty()) {
                    ctClass.addField(CtField.make(field, ctClass));
                }
                start = i + 1;
            }
        }
    }

    private static SourceParts parseSource(String className, String source) {
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        int classBodyStart = source.indexOf('{', source.indexOf("class " + simpleClassName));
        int classBodyEnd = source.lastIndexOf('}');
        if (classBodyStart < 0 || classBodyEnd < classBodyStart) {
            throw BaseException.get("parse dynamic source failed(className={}): class body not found", className);
        }

        String classBody = source.substring(classBodyStart + 1, classBodyEnd);
        String constructorMarker = "public " + simpleClassName + "()";
        int constructorStart = classBody.indexOf(constructorMarker);
        if (constructorStart < 0) {
            throw BaseException.get("parse dynamic source failed(className={}): constructor not found", className);
        }

        int constructorBodyStart = classBody.indexOf('{', constructorStart);
        int constructorEnd = findBlockEnd(classBody, constructorBodyStart);
        int processStart = classBody.indexOf("public Object process", constructorEnd + 1);
        int deProcessStart = classBody.indexOf("public void deProcess", processStart + 1);
        if (constructorEnd < 0 || processStart < 0 || deProcessStart < 0) {
            throw BaseException.get("parse dynamic source failed(className={}): processor methods not found", className);
        }

        int processBodyStart = classBody.indexOf('{', processStart);
        int processEnd = findBlockEnd(classBody, processBodyStart);
        int deProcessBodyStart = classBody.indexOf('{', deProcessStart);
        int deProcessEnd = findBlockEnd(classBody, deProcessBodyStart);
        if (processEnd < 0 || deProcessEnd < 0) {
            throw BaseException.get("parse dynamic source failed(className={}): processor method body not found", className);
        }

        return new SourceParts(
                classBody.substring(0, constructorStart),
                classBody.substring(constructorStart, constructorEnd + 1),
                classBody.substring(processStart, processEnd + 1),
                classBody.substring(deProcessStart, deProcessEnd + 1)
        );
    }

    private static String sanitizeMemberSource(String source) {
        return source.replace("final ", "");
    }

    private static int findBlockEnd(String text, int blockStart) {
        if (blockStart < 0) {
            return -1;
        }
        int depth = 0;
        for (int i = blockStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private record SourceParts(String fields, String constructor, String processMethod, String deProcessMethod) {
    }
}
