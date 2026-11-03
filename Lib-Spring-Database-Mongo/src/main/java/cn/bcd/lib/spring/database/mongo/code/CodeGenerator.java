package cn.bcd.lib.spring.database.mongo.code;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.database.mongo.bean.SuperBaseBean;
import cn.bcd.lib.spring.database.mongo.code.data.*;
import freemarker.template.*;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.annotation.Transient;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class CodeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(CodeGenerator.class);
    private static final Set<String> AUDIT_FIELDS = Set.of(
            "createTime", "createUserId", "createUserName", "updateTime", "updateUserId", "updateUserName");

    private static void render(ApiData data, String suffix, String templateDir, String targetDir) {
        try {
            Configuration configuration = new Configuration(CodeConst.FREEMARKER_VERSION);
            configuration.setDirectoryForTemplateLoading(Path.of(templateDir).toFile());
            configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
            DefaultObjectWrapper wrapper = new DefaultObjectWrapper(CodeConst.FREEMARKER_VERSION);
            wrapper.setExposeFields(true);
            StringWriter source = new StringWriter();
            configuration.getTemplate("mongo_Template" + suffix + ".txt").process(data, source, wrapper);
            Files.createDirectories(Path.of(targetDir));
            Path target = Path.of(targetDir, data.moduleName + suffix + ".java");
            Files.writeString(target, source.toString(), StandardCharsets.UTF_8);
            logger.info("generated {}", target);
        } catch (IOException | TemplateException ex) {
            throw BaseException.get(ex);
        }
    }

    public static void generateService(ServiceData data, String templates, String target) {
        render(data, "Service", templates, target);
    }

    public static void generateController(ControllerData data, String templates, String target) {
        render(data, "Controller", templates, target);
    }

    public static void generateQueryRequest(ApiData data, String templates, String target) {
        render(data, "QueryRequest", templates, target);
    }

    public static void generateSaveRequest(ApiData data, String templates, String target) {
        render(data, "SaveRequest", templates, target);
    }

    public static void generateQueryResponse(ApiData data, String templates, String target) {
        render(data, "QueryResponse", templates, target);
    }

    public static ServiceData initServiceData(CollectionConfig config) {
        return initApiData(config, new ServiceData());
    }

    public static ControllerData initControllerData(CollectionConfig config) {
        return initApiData(config, new ControllerData());
    }

    private static <D extends ApiData> D initApiData(CollectionConfig config, D data) {
        if (config.moduleName == null || config.moduleName.isBlank()) {
            throw new IllegalArgumentException("moduleName must not be blank");
        }
        data.moduleName = Character.toUpperCase(config.moduleName.charAt(0)) + config.moduleName.substring(1);
        data.moduleNameCN = config.moduleNameCN;
        data.beanClassName = config.clazz.getCanonicalName();
        data.beanSimpleName = config.clazz.getSimpleName();
        data.beanPackage = config.clazz.getPackageName();
        String normalized = Path.of(config.targetDirPath).toString().replace('\\', '/');
        String marker = "src/main/java/";
        int index = normalized.lastIndexOf(marker);
        data.packagePre = index < 0 ? data.beanPackage : normalized.substring(index + marker.length()).replace('/', '.');
        data.fieldList = initBeanField(config);
        data.queryResponseFields = data.fieldList;
        data.queryFields = data.fieldList.stream().filter(field -> !AUDIT_FIELDS.contains(field.name) && field.queryKind != null).toList();
        data.saveFields = data.fieldList.stream().filter(field -> !AUDIT_FIELDS.contains(field.name) && !field.name.equals("id") && field.writable).toList();
        Set<String> names = new HashSet<>();
        for (BeanField field : data.queryFields) {
            if (field.name.equals("pageNum") || field.name.equals("pageSize")) {
                throw new IllegalArgumentException("Query field conflicts with pagination: " + field.name);
            }
            List<String> properties = field.queryKind.equals("DATE")
                    ? List.of(field.name + "Begin", field.name + "End") : List.of(field.name);
            for (String property : properties) {
                if (!names.add(property)) throw new IllegalArgumentException("Duplicate query property: " + property);
            }
        }
        return data;
    }

    /** 仅读取持久化字段，不依赖 Schema 或校验注解，不静默过滤复杂类型。 */
    public static List<BeanField> initBeanField(CollectionConfig config) {
        if (!SuperBaseBean.class.isAssignableFrom(config.clazz)) {
            throw new IllegalArgumentException("Bean must extend SuperBaseBean: " + config.clazz.getName());
        }
        Map<String, BeanField> fields = new LinkedHashMap<>();
        for (Field field : FieldUtils.getAllFieldsList(config.clazz)) {
            int modifiers = field.getModifiers();
            var property = BeanUtils.getPropertyDescriptor(config.clazz, field.getName());
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()
                    || field.isAnnotationPresent(Transient.class)
                    || (property != null && property.getReadMethod() != null && property.getReadMethod().isAnnotationPresent(Transient.class))) continue;
            if (fields.containsKey(field.getName())) continue;
            BeanField data = new BeanField();
            data.name = field.getName();
            Type resolved = GenericTypeResolver.resolveType(field.getGenericType(), config.clazz);
            Class<?> rawType = field.getType();
            if (resolved instanceof Class<?> type) rawType = type;
            rawType = ClassUtils.resolvePrimitiveIfNecessary(rawType);
            data.type = resolved instanceof Class<?> type && type.isPrimitive()
                    ? javaType(ClassUtils.resolvePrimitiveIfNecessary(type), config.clazz) : javaType(resolved, config.clazz);
            data.setComment(data.name);
            if (rawType == String.class) data.queryKind = "STRING";
            else if (rawType == Character.class) data.queryKind = "CHARACTER";
            else if (rawType == Boolean.class) data.queryKind = "BOOLEAN";
            else if (Number.class.isAssignableFrom(rawType)) data.queryKind = "NUMBER";
            else if (Date.class.isAssignableFrom(rawType)) data.queryKind = "DATE";
            if (Modifier.isPublic(modifiers)) {
                data.beanExpression = "bean." + data.name;
            } else if (property != null && property.getReadMethod() != null && Modifier.isPublic(property.getReadMethod().getModifiers())) {
                data.beanExpression = "bean." + property.getReadMethod().getName() + "()";
            } else {
                throw new IllegalArgumentException("Persistent field needs public access or a getter: " + data.name);
            }
            if (Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers)) {
                data.writable = true;
                data.createStatement = "bean." + data.name + " = request." + data.name + ";";
            } else if (property != null && property.getWriteMethod() != null && Modifier.isPublic(property.getWriteMethod().getModifiers())) {
                data.writable = true;
                data.createStatement = "bean." + property.getWriteMethod().getName() + "(request." + data.name + ");";
            }
            if (field.getType().isPrimitive() && data.writable) {
                data.createStatement = "if (request." + data.name + " != null) { " + data.createStatement + " }";
            }
            fields.put(data.name, data);
        }
        return List.copyOf(fields.values());
    }

    private static String javaType(Type type, Class<?> context) {
        if (type instanceof TypeVariable<?>) {
            Type resolved = GenericTypeResolver.resolveType(type, context);
            if (!resolved.equals(type)) return javaType(resolved, context);
            throw new IllegalArgumentException("Unresolved generic type: " + type);
        }
        if (type instanceof ParameterizedType generic) {
            StringJoiner arguments = new StringJoiner(", ");
            for (Type argument : generic.getActualTypeArguments()) arguments.add(javaType(argument, context));
            return javaType(generic.getRawType(), context) + "<" + arguments + ">";
        }
        if (type instanceof GenericArrayType array) return javaType(array.getGenericComponentType(), context) + "[]";
        if (type instanceof WildcardType wildcard) {
            if (wildcard.getLowerBounds().length > 0) return "? super " + javaType(wildcard.getLowerBounds()[0], context);
            Type upper = wildcard.getUpperBounds()[0];
            return upper == Object.class ? "?" : "? extends " + javaType(upper, context);
        }
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) return javaType(clazz.getComponentType(), context) + "[]";
            String name = clazz.getCanonicalName();
            if (name != null) {
                String packageName = clazz.getPackageName();
                if (Set.of("java.lang", "java.util", "java.math", "java.time").contains(packageName)
                        && name.startsWith(packageName + ".")) {
                    return name.substring(packageName.length() + 1);
                }
                return name;
            }
        }
        throw new IllegalArgumentException("Unsupported Java type: " + type);
    }

    public static void generate(CollectionConfig config) {
        String templates = config.templateDirPath == null ? CodeConst.TEMPLATE_DIR_PATH : config.templateDirPath;
        ServiceData data = initServiceData(config);
        if (config.needCreateQueryRequestFile) generateQueryRequest(data, templates, config.targetDirPath);
        if (config.needCreateSaveRequestFile) generateSaveRequest(data, templates, config.targetDirPath);
        if (config.needCreateQueryResponseFile) generateQueryResponse(data, templates, config.targetDirPath);
        if (config.needCreateServiceFile) generateService(data, templates, config.targetDirPath);
        if (config.needCreateControllerFile) generateController(initControllerData(config), templates, config.targetDirPath);
    }
}
