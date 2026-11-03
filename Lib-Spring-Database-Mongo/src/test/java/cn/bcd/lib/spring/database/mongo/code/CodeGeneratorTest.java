package cn.bcd.lib.spring.database.mongo.code;

import cn.bcd.lib.spring.database.mongo.code.MongoGeneratorFixtures.*;
import cn.bcd.lib.spring.database.mongo.code.data.BeanField;
import cn.bcd.lib.spring.database.mongo.service.BaseService;
import cn.bcd.lib.spring.database.mongo.service.UserGetter;
import cn.bcd.lib.spring.database.mongo.bean.UserInterface;
import cn.bcd.lib.spring.database.mongo.util.ConditionUtil;
import cn.bcd.lib.spring.database.common.condition.Condition;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.convert.UpdateMapper;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CodeGeneratorTest {
    @TempDir
    Path directory;

    private CollectionConfig config(Class<?> bean, String module) throws Exception {
        CollectionConfig config = new CollectionConfig(module, "示例", bean,
                directory.resolve("src/main/java/generated/mongo").toString());
        config.templateDirPath = Path.of(getClass().getResource("/template/mongo_TemplateService.txt").toURI()).getParent().toString();
        return config;
    }

    @Test
    void metadataPreservesGenericsPrimitivesAndComplexFieldsWithoutSchema() throws Exception {
        var data = CodeGenerator.initServiceData(config(FixtureBean.class, "Fixture"));
        Map<String, BeanField> fields = new HashMap<>();
        data.fieldList.forEach(field -> fields.put(field.name, field));
        assertEquals("String", fields.get("inherited").type);
        assertEquals("String[]", fields.get("inheritedArray").type);
        assertEquals("int[]", fields.get("samples").type);
        assertEquals("Boolean", fields.get("enabled").type);
        assertEquals("Character", fields.get("grade").type);
        assertTrue(fields.get("details").type.startsWith("List<Map<String, "));
        assertTrue(fields.get("polymorphic").type.contains("? extends "));
        assertEquals("bean.getAmount()", fields.get("amount").beanExpression);
        assertTrue(fields.get("amount").createStatement.contains("bean.setAmount("));
        for (String name : List.of("ignored", "javaIgnored", "shared")) assertFalse(fields.containsKey(name));
        assertTrue(data.queryFields.stream().anyMatch(field -> field.name.equals("enabled")));
        assertTrue(data.queryFields.stream().anyMatch(field -> field.name.equals("score")));
        assertFalse(data.queryFields.stream().anyMatch(field -> field.name.equals("details")));
        assertFalse(data.queryFields.stream().anyMatch(field -> field.name.equals("createTime")));
        assertFalse(data.saveFields.stream().anyMatch(field -> field.name.equals("id") || field.name.equals("readOnly") || field.name.equals("updateTime")));
        assertTrue(data.saveFields.stream().anyMatch(field -> field.name.equals("details")));
        assertTrue(data.queryResponseFields.stream().anyMatch(field -> field.name.equals("createTime")));
        assertEquals("name", fields.get("name").comment);
    }

    @Test
    void generatedModelsCompileAndConvertConditionsAndResponses() throws Exception {
        try (Generated generated = generate(FixtureBean.class, "Fixture")) {
            Object query = generated.model("QueryRequest");
            Condition empty = (Condition) query.getClass().getMethod("toCondition").invoke(query);
            assertTrue(ConditionUtil.toQuery(empty).getQueryObject().isEmpty());
            query.getClass().getField("enabled").set(query, false);
            query.getClass().getField("count").set(query, 0);
            query.getClass().getField("grade").set(query, 'A');
            query.getClass().getField("timeBegin").set(query, new Date(1));
            Condition condition = (Condition) query.getClass().getMethod("toCondition").invoke(query);
            String bson = ConditionUtil.toQuery(condition).getQueryObject().toJson();
            assertTrue(bson.contains("\"enabled\": false"));
            assertTrue(bson.contains("\"count\": 0"));
            assertTrue(bson.contains("\"grade\": \"A\""));
            assertTrue(bson.contains("$gte"));

            FixtureBean bean = new FixtureBean();
            bean.id = "document-1";
            bean.name = "name";
            bean.setAmount(new BigDecimal("12.34"));
            bean.details = List.of(Map.of("item", new Detail()));
            bean.createTime = new Date(1234);
            Class<?> responseType = generated.type("QueryResponse");
            Object response = responseType.getMethod("fromBean", FixtureBean.class).invoke(null, bean);
            assertEquals("document-1", responseType.getField("id").get(response));
            assertEquals(new BigDecimal("12.34"), responseType.getField("amount").get(response));
            assertSame(bean.details, responseType.getField("details").get(response));
            assertEquals(bean.createTime, responseType.getField("createTime").get(response));
            assertEquals("fixed", responseType.getField("readOnly").get(response));
        }
    }

    @Test
    void generatedSaveCreatesWithAuditAndUpdatesOnlyExplicitFields() throws Exception {
        @SuppressWarnings("unchecked")
        Supplier<UserInterface> previous = (Supplier<UserInterface>) ReflectionTestUtils.getField(UserGetter.class, "supplier");
        new UserGetter().setConsumer(() -> new UserInterface() {
            public String getId() { return "operator-1"; }
            public String getUsername() { return "operator"; }
        });
        try (Generated generated = generate(FixtureBean.class, "Fixture")) {
            MongoTemplate mongo = mock(MongoTemplate.class);
            BaseService<?> service = generated.service(mongo);
            when(mongo.save(any(FixtureBean.class))).thenAnswer(call -> call.getArgument(0));
            Object request = generated.model("SaveRequest");
            request.getClass().getField("name").set(request, "created");
            request.getClass().getField("amount").set(request, new BigDecimal("1.25"));
            generated.save(service, request);
            ArgumentCaptor<FixtureBean> inserted = ArgumentCaptor.forClass(FixtureBean.class);
            verify(mongo).save(inserted.capture());
            assertEquals("created", inserted.getValue().name);
            assertEquals(new BigDecimal("1.25"), inserted.getValue().getAmount());
            assertEquals(0, inserted.getValue().count);
            assertNotNull(inserted.getValue().createTime);
            assertEquals("operator-1", inserted.getValue().createUserId);
            verify(mongo, never()).exists(any(Query.class), any(Class.class));

            clearInvocations(mongo);
            when(mongo.updateFirst(any(Query.class), any(Update.class), eq(FixtureBean.class)))
                    .thenReturn(UpdateResult.acknowledged(1, 0L, null));
            request.getClass().getField("id").set(request, "document-1");
            request.getClass().getField("name").set(request, null);
            generated.save(service, request);
            ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
            verify(mongo).updateFirst(query.capture(), update.capture(), eq(FixtureBean.class));
            assertEquals("document-1", query.getValue().getQueryObject().get("id"));
            Document changes = (Document) update.getValue().getUpdateObject().get("$set");
            assertTrue(changes.containsKey("name"));
            assertNull(changes.get("name"));
            assertTrue(changes.containsKey("details"));
            assertFalse(changes.containsKey("id"));
            assertFalse(changes.containsKey("createTime"));
            assertFalse(changes.containsKey("readOnly"));
            assertFalse(update.getValue().getUpdateObject().containsKey("$unset"));
            assertNotNull(changes.get("updateTime"));
            assertEquals("operator-1", changes.get("updateUserId"));
            verify(mongo, never()).save(any(FixtureBean.class));

            MongoMappingContext mapping = new MongoMappingContext();
            var conversions = MongoCustomConversions.create(adapter -> {});
            mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
            mapping.setInitialEntitySet(Set.of(FixtureBean.class));
            mapping.afterPropertiesSet();
            MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mapping);
            converter.setCustomConversions(conversions);
            converter.afterPropertiesSet();
            var entity = mapping.getRequiredPersistentEntity(FixtureBean.class);
            Document mappedUpdate = new UpdateMapper(converter).getMappedObject(update.getValue().getUpdateObject(), entity);
            assertTrue(((Document) mappedUpdate.get("$set")).containsKey("display_name"));
            assertFalse(((Document) mappedUpdate.get("$set")).containsKey("name"));
            assertEquals("document-1", new QueryMapper(converter).getMappedObject(query.getValue().getQueryObject(), entity).get("_id"));
        } finally {
            new UserGetter().setConsumer(previous);
        }
    }

    @Test
    void generatedUpdateRespectsDisabledAuditAndReportsMissingTargets() throws Exception {
        try (Generated generated = generate(ManualBean.class, "Manual")) {
            MongoTemplate mongo = mock(MongoTemplate.class);
            BaseService<?> service = generated.service(mongo);
            Object request = generated.model("SaveRequest");
            request.getClass().getField("id").set(request, "missing");
            when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ManualBean.class)))
                    .thenReturn(UpdateResult.acknowledged(0, 0L, null));
            InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> generated.save(service, request));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
            verify(mongo).updateFirst(any(Query.class), update.capture(), eq(ManualBean.class));
            Document changes = (Document) update.getValue().getUpdateObject().get("$set");
            assertFalse(changes.containsKey("updateTime"));
            assertFalse(changes.containsKey("updateUserId"));
            verify(mongo, never()).upsert(any(Query.class), any(Update.class), any(Class.class));
        }
    }

    @Test
    void generationOverwritesByDefaultAndHonorsIndividualFileSwitches() throws Exception {
        CollectionConfig config = config(FixtureBean.class, "Fixture");
        CodeGenerator.generate(config);
        Path target = Path.of(config.targetDirPath);
        try (var files = Files.list(target)) { assertEquals(5, files.count()); }
        Path response = target.resolve("FixtureQueryResponse.java");
        Files.writeString(response, "old code");
        CodeGenerator.generate(config);
        assertTrue(Files.readString(response).contains("fromBean"));
        config.needCreateQueryResponseFile = false;
        Files.writeString(response, "custom code");
        CodeGenerator.generate(config);
        assertEquals("custom code", Files.readString(response));
    }

    private Generated generate(Class<?> bean, String module) throws Exception {
        CollectionConfig config = config(bean, module);
        CodeGenerator.generate(config);
        Path output = directory.resolve("classes");
        Files.createDirectories(output);
        var diagnostics = new java.io.ByteArrayOutputStream();
        List<String> args = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8", "-d", output.toString(), "-classpath", compilerClasspath()));
        try (var sources = Files.list(Path.of(config.targetDirPath))) {
            sources.filter(path -> path.toString().endsWith(".java")).forEach(path -> args.add(path.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics, args.toArray(String[]::new)), diagnostics.toString());
        String querySource = Files.readString(Path.of(config.targetDirPath, module + "QueryRequest.java"));
        String saveSource = Files.readString(Path.of(config.targetDirPath, module + "SaveRequest.java"));
        assertFalse(querySource.contains("createTime"));
        assertFalse(querySource.contains("pageNum"));
        assertFalse(saveSource.contains("createTime"));
        assertFalse(saveSource.contains("updateTime"));
                assertTrue(saveSource.contains("public List<Map<String, "));
        assertTrue(saveSource.contains("import java.util.*;"));
        assertTrue(saveSource.contains("public BigDecimal amount;"));
        assertTrue(querySource.contains("public Date timeEnd;"));
        assertFalse(querySource.contains("public java.util.Date"));
        assertFalse(saveSource.contains("java.lang.String"));

        return new Generated(module, new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader()));
    }

    private String compilerClasspath() throws Exception {
        Set<String> paths = new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString());
            }
        }
        return String.join(File.pathSeparator, paths);
    }

    private record Generated(String module, URLClassLoader loader) implements AutoCloseable {
        Class<?> type(String suffix) throws Exception { return loader.loadClass("generated.mongo." + module + suffix); }
        Object model(String suffix) throws Exception { return type(suffix).getConstructor().newInstance(); }
        BaseService<?> service(MongoTemplate mongo) throws Exception {
            BaseService<?> service = (BaseService<?>) model("Service");
            service.init(mongo);
            return service;
        }
        void save(BaseService<?> service, Object request) throws Exception {
            type("Service").getMethod("save" + module, type("SaveRequest")).invoke(service, request);
        }
        public void close() throws Exception { loader.close(); }
    }
}
