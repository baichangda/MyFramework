package cn.bcd.lib.spring.database.jdbc.code;

import cn.bcd.lib.spring.database.jdbc.code.data.BeanField;
import cn.bcd.lib.spring.database.jdbc.code.data.ServiceData;
import cn.bcd.lib.spring.database.jdbc.dbinfo.data.DBInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CodeGeneratorTest {
    @TempDir
    Path directory;

    private BeanField field(String name, String type, boolean nullable) {
        BeanField field = new BeanField();
        field.name = name;
        field.type = type;
        field.nullable = nullable;
        if (type.equals("String")) field.strLen = 50;
        field.setComment("字段 \"" + name + "\"\\说明");
        return field;
    }

    private CodeGeneratorContext context() {
        TableConfig table = new TableConfig();
        table.moduleName = "Example";
        table.moduleNameCN = "示例";
        table.tableName = "t_example";
        Config.newConfig(directory.toString()).addTableConfig(table);
        CodeGeneratorContext context = new CodeGeneratorContext(table, null, null);
        context.packagePre = "example";
        context.allBeanFields = new ArrayList<>(List.of(
                field("id", "Long", false), field("name", "String", false),
                field("email", "String", true), field("birthday", "Date", true),
                field("amount", "BigDecimal", true), field("password", "String", false)));
        for (String name : List.of("createTime", "createUserId", "createUserName",
                "updateTime", "updateUserId", "updateUserName")) {
            context.allBeanFields.add(field(name, name.endsWith("Time") ? "Date" : name.endsWith("Id") ? "Long" : "String", true));
        }
        return context;
    }

    @Test
    void requestDefaultsContainAllBusinessFieldsAndExcludeAuditFields() {
        ServiceData data = CodeGenerator.MYSQL.initServiceData(context());
        assertEquals(List.of("id", "name", "email", "birthday", "amount", "password"),
                data.queryFields.stream().map(field -> field.name).toList());
        assertEquals(List.of("name", "email", "birthday", "amount", "password"),
                data.saveFields.stream().map(field -> field.name).toList());
        assertEquals(12, data.queryResponseFields.size());
    }

    @Test
    void rendersSixCompilableFilesAndOverwritesExistingSources() throws Exception {
        var context = context();
        var data = CodeGenerator.MYSQL.initServiceData(context);
        Path templates = Path.of(getClass().getResource("/template/rdb_TemplateBean.txt").toURI()).getParent();
        CodeGenerator generator = CodeGenerator.MYSQL;
        generator.generateBean(generator.initBeanData(context), templates.toString(), directory.toString());
        generator.generateQueryRequest(data, templates.toString(), directory.toString());
        generator.generateSaveRequest(data, templates.toString(), directory.toString());
        generator.generateQueryResponse(data, templates.toString(), directory.toString());
        generator.generateService(data, templates.toString(), directory.toString());
        generator.generateController(generator.initControllerData(context), templates.toString(), directory.toString());

        Path module = directory.resolve("example");
        String query = Files.readString(module.resolve("ExampleQueryRequest.java"));
        String save = Files.readString(module.resolve("ExampleSaveRequest.java"));
        String service = Files.readString(module.resolve("ExampleService.java"));
        assertFalse(query.contains("createTime"));
        assertFalse(query.contains("pageNum"));
        assertTrue(query.contains("birthdayBegin"));
        assertTrue(query.contains("birthdayEnd"));
        assertTrue(query.contains("public Condition toCondition()"));
        assertFalse(service.contains("buildCondition"));
        assertFalse(service.contains("Response toResponse("));
        assertTrue(service.contains("ExampleQueryResponse::fromBean"));
        assertTrue(Files.readString(module.resolve("ExampleQueryResponse.java")).contains("public static ExampleQueryResponse fromBean(ExampleBean bean)"));

        assertTrue(save.contains("password"));
        assertTrue(service.contains("return list(query.toCondition()).stream()"));
        assertFalse(service.contains("maxPageSize"));
        assertFalse(service.contains("Invalid pagination parameters"));
        String controllerSource = Files.readString(module.resolve("ExampleController.java"));
        assertFalse(controllerSource.contains("@Min("));
        assertFalse(controllerSource.contains("@Max("));
        assertFalse(controllerSource.contains("@Size("));
        assertTrue(service.contains("changes.put(\"password\", request.password)"));
        assertFalse(save.contains("updateTime"));
        assertTrue(service.contains("update(request.id, changes)"));
        assertFalse(service.contains("updateFieldsById"));
        String beanSource = Files.readString(module.resolve("ExampleBean.java"));
        assertFalse(beanSource.contains("jakarta.validation"));
        assertFalse(beanSource.contains("@Schema"));
        assertFalse(beanSource.contains("io.swagger"));
        assertTrue(beanSource.contains("// 字段"));
        assertTrue(query.contains("@Schema"));
        assertTrue(save.contains("@Schema"));
        assertTrue(Files.readString(module.resolve("ExampleQueryResponse.java")).contains("@Schema"));

        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var diagnostics = new java.io.ByteArrayOutputStream();
        List<String> args = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8", "-classpath", compilerClasspath()));
        try (var files = Files.list(module)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> args.add(path.toString()));
        }
        assertEquals(0, compiler.run(null, diagnostics, diagnostics, args.toArray(String[]::new)), diagnostics.toString());

        Path controller = module.resolve("ExampleController.java");
        Files.writeString(controller, "existing business code");
        generator.generateController(generator.initControllerData(context), templates.toString(), directory.toString());
        assertEquals(controllerSource, Files.readString(controller));
    }

    @Test
    void generateOverwritesByDefaultAndHonorsFileFlagsWithoutConnectingToDatabase() throws Exception {
        var fixture = context();
        DBSupport support = mock(DBSupport.class);
        when(support.getTableBeanFieldList(any(), any())).thenReturn(fixture.allBeanFields);
        Connection connection = mock(Connection.class);
        CodeGenerator generator = new CodeGenerator(support) {
            @Override
            public Connection getConnection(String url, String username, String password) {
                return connection;
            }
        };
        Path target = directory.resolve("src/main/java/example");
        DBInfo database = new DBInfo("unused", "unused", "unused", "unused");
        Config config = Config.newConfig(target.toString(), database).addTableConfig(fixture.tableConfig);
        config.templateDirPath = Path.of(getClass().getResource("/template/rdb_TemplateBean.txt").toURI()).getParent().toString();
        assertSame(database, config.dbInfo);
        generator.generate(config);
        Path service = target.resolve("example/ExampleService.java");
        try (var files = Files.list(service.getParent())) {
            assertEquals(6, files.count());
        }
        Files.writeString(service, "custom service");
        generator.generate(config);
        assertTrue(Files.readString(service).contains("update(request.id, changes)"));

        fixture.tableConfig.needCreateBeanFile = false;
        fixture.tableConfig.needCreateControllerFile = false;
        fixture.tableConfig.needCreateQueryRequestFile = false;
        fixture.tableConfig.needCreateSaveRequestFile = false;
        fixture.tableConfig.needCreateQueryResponseFile = false;
        Path query = target.resolve("example/ExampleQueryRequest.java");
        Files.writeString(query, "custom query");
        generator.generate(config);
        assertTrue(Files.readString(service).contains("update(request.id, changes)"));
        assertEquals("custom query", Files.readString(query));
        verify(support, never()).getSpringDBConfig();
    }

    @Test
    void rejectsQueryNameCollisions() {
        var collision = context();
        collision.allBeanFields.add(field("birthdayBegin", "String", true));
        assertThrows(IllegalArgumentException.class, () -> CodeGenerator.MYSQL.initServiceData(collision));
        var pagination = context();
        pagination.allBeanFields.add(field("pageNum", "Integer", true));
        assertThrows(IllegalArgumentException.class, () -> CodeGenerator.MYSQL.initServiceData(pagination));
    }

    private String compilerClasspath() throws Exception {
        var paths = new LinkedHashSet<String>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) {
                    if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString());
                }
            }
        }
        return String.join(File.pathSeparator, paths);
    }
}
