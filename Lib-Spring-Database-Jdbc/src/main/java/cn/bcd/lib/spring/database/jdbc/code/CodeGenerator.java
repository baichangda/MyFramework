package cn.bcd.lib.spring.database.jdbc.code;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.database.jdbc.code.data.*;
import cn.bcd.lib.spring.database.jdbc.code.mysql.MysqlDBSupport;
import cn.bcd.lib.spring.database.jdbc.code.pgsql.PgsqlDBSupport;
import freemarker.template.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

public class CodeGenerator {
    public static final CodeGenerator MYSQL = new CodeGenerator(new MysqlDBSupport());
    public static final CodeGenerator PGSQL = new CodeGenerator(new PgsqlDBSupport());
    private static final Logger logger = LoggerFactory.getLogger(CodeGenerator.class);
    final DBSupport dbSupport;

    public CodeGenerator(DBSupport dbSupport) {
        this.dbSupport = dbSupport;
    }

    private void render(Object data, String moduleName, String suffix, String templateDir,
                        String targetDir) {
        Path moduleDir = Path.of(targetDir, Character.toLowerCase(moduleName.charAt(0)) + moduleName.substring(1));
        Path target = moduleDir.resolve(Character.toUpperCase(moduleName.charAt(0)) + moduleName.substring(1) + suffix + ".java");
        try {
            Configuration configuration = new Configuration(CodeConst.FREEMARKER_VERSION);
            configuration.setDirectoryForTemplateLoading(Path.of(templateDir).toFile());
            configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
            DefaultObjectWrapper wrapper = new DefaultObjectWrapper(CodeConst.FREEMARKER_VERSION);
            wrapper.setExposeFields(true);
            // 先在内存中渲染，模板错误不会清空已有文件。
            java.io.StringWriter buffer = new java.io.StringWriter();
            configuration.getTemplate("rdb_Template" + suffix + ".txt").process(data, buffer, wrapper);
            Files.createDirectories(moduleDir);
            Files.writeString(target, buffer.toString(), StandardCharsets.UTF_8);
            logger.info("generated {}", target);
        } catch (IOException | TemplateException ex) {
            throw BaseException.get(ex);
        }
    }

    public void generateBean(BeanData data, String templates, String target) {
        render(data, data.moduleName, "Bean", templates, target);
    }

    public void generateService(ServiceData data, String templates, String target) {
        render(data, data.moduleName, "Service", templates, target);
    }

    public void generateController(ControllerData data, String templates, String target) {
        render(data, data.moduleName, "Controller", templates, target);
    }

    public void generateQueryRequest(ApiData data, String templates, String target) {
        render(data, data.moduleName, "QueryRequest", templates, target);
    }

    public void generateSaveRequest(ApiData data, String templates, String target) {
        render(data, data.moduleName, "SaveRequest", templates, target);
    }

    public void generateQueryResponse(ApiData data, String templates, String target) {
        render(data, data.moduleName, "QueryResponse", templates, target);
    }

    public BeanData initBeanData(CodeGeneratorContext context) {
        BeanData data = new BeanData();
        data.moduleNameCN = context.tableConfig.moduleNameCN;
        data.moduleName = context.tableConfig.moduleName;
        data.packagePre = context.getModulePackage();
        data.tableName = context.tableConfig.tableName;
        data.containCreateAndUpdateField = context.getContainCreateAndUpdateField();
        data.fieldList = context.getDeclaredBeanFields();
        return data;
    }

    public ServiceData initServiceData(CodeGeneratorContext context) {
        return context.initApiData(new ServiceData());
    }

    public ControllerData initControllerData(CodeGeneratorContext context) {
        ControllerData data = context.initApiData(new ControllerData());
        data.fieldList = context.getAllBeanFields();
        data.requestMappingPre = context.getRequestMappingPre();
        return data;
    }

    public void generate(Config config) {
        if (config.dbInfo == null) config.dbInfo = dbSupport.getSpringDBConfig();
        String templates = config.templateDirPath == null ? CodeConst.TEMPLATE_DIR_PATH : config.templateDirPath;
        try (Connection connection = getConnection(config.dbInfo.url, config.dbInfo.username, config.dbInfo.password)) {
            for (TableConfig table : config.tableConfigs) {
                CodeGeneratorContext context = new CodeGeneratorContext(table, dbSupport, connection);
                ServiceData data = initServiceData(context);
                if (table.needCreateBeanFile) render(initBeanData(context), table.moduleName, "Bean", templates, config.targetDirPath);
                if (table.needCreateQueryRequestFile) render(data, table.moduleName, "QueryRequest", templates, config.targetDirPath);
                if (table.needCreateSaveRequestFile) render(data, table.moduleName, "SaveRequest", templates, config.targetDirPath);
                if (table.needCreateQueryResponseFile) render(data, table.moduleName, "QueryResponse", templates, config.targetDirPath);
                if (table.needCreateServiceFile) render(data, table.moduleName, "Service", templates, config.targetDirPath);
                if (table.needCreateControllerFile) render(initControllerData(context), table.moduleName, "Controller", templates, config.targetDirPath);
            }
        } catch (SQLException ex) {
            throw BaseException.get(ex);
        }
    }

    public Connection getConnection(String url, String username, String password) {
        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException ex) {
            throw BaseException.get(ex);
        }
    }
}
