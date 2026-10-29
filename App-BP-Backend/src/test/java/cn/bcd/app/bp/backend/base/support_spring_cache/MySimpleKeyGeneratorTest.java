package cn.bcd.app.bp.backend.base.support_spring_cache;

import cn.bcd.app.bp.backend.base.support_spring_cache.anno.LocalCacheable;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.*;

import java.io.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MySimpleKeyGeneratorTest {
    @Test
    void largeArgumentsProduceFixedLengthKeysWithoutRetainingThePayload() throws Exception {
        var generator = new MySimpleKeyGenerator();
        var method = Service.class.getMethod("object", Object.class);
        Object key = generator.generate(new Service(), method, "large-argument".repeat(10_000));
        assertTrue(key.toString().matches("[0-9a-f]{64}"));
        assertEquals(key, generator.generate(new Service(), method, "large-argument".repeat(10_000)));
        assertNotEquals(key, generator.generate(new Service(), method, "different"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(key);
        }
        assertTrue(bytes.size() < 512, "serialized key must not contain the large argument");
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object restored = input.readObject();
            assertEquals(key, restored);
            assertEquals(key.hashCode(), restored.hashCode());
            assertEquals(key.toString(), restored.toString());
        }
    }

    @Test
    void changingAnArgumentAfterKeyGenerationDoesNotChangeTheKey() throws Exception {
        var generator = new MySimpleKeyGenerator();
        var method = Service.class.getMethod("object", Object.class);
        int[] argument = {1, 2};
        Object key = generator.generate(new Service(), method, argument);
        String digest = key.toString();
        int hashCode = key.hashCode();
        argument[0] = 3;
        assertEquals(digest, key.toString());
        assertEquals(hashCode, key.hashCode());
        assertEquals(key, generator.generate(new Service(), method, new int[]{1, 2}));
        assertNotEquals(key, generator.generate(new Service(), method, argument));
    }

    @Test
    void overloadedMethodsHaveSeparateCachesIncludingNullArguments() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Service service = context.getBean(Service.class);
            assertEquals("integer", service.number(1));
            assertEquals("long", service.number(1L));
            assertEquals("integer", service.number((Integer) null));
            assertEquals("long", service.number((Long) null));
            assertEquals("text", service.text((CharSequence) "value"));
            assertEquals("serializable", service.text((Serializable) "value"));
        }
    }

    @Test
    void objectParameterPreservesRuntimeArgumentTypes() throws Exception {
        var generator = new MySimpleKeyGenerator();
        var method = Service.class.getMethod("object", Object.class);
        Service service = new Service();
        Object integerKey = generator.generate(service, method, 1);
        Object longKey = generator.generate(service, method, 1L);
        assertNotEquals(integerKey, longKey);
        assertNotEquals(integerKey.toString(), longKey.toString());
        assertEquals(integerKey, generator.generate(service, method, 1));
        assertEquals(integerKey.hashCode(), generator.generate(service, method, 1).hashCode());
    }

    @Test
    void methodNameHelpersGenerateTheSameKeyAsTheInterceptor() throws Exception {
        var generator = new MySimpleKeyGenerator();
        Service service = new Service();
        var method = Service.class.getMethod("primitive", int.class);
        Object expected = generator.generate(service, method, 1);
        assertEquals(expected, generator.generate(service, "primitive", 1));
        assertEquals(expected, generator.generate(Service.class, "primitive", 1));
        assertEquals(expected, generator.generate(Service.class.getName(), "primitive", 1));
        assertEquals(expected, generator.generate(Service.class, method, 1));
        var objectMethod = Service.class.getMethod("object", Object.class);
        assertEquals(generator.generate(service, objectMethod, "value"), generator.generate(service, "object", "value"));
    }

    @Configuration
    @Import(CacheConfig.class)
    static class Config {
        @Bean CacheManager cacheManager(CacheConfig config) {
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(config.localCache()));
            return manager;
        }
        @Bean Service service() { return new Service(); }
    }

    public static class Service {
        @LocalCacheable public String number(Integer value) { return "integer"; }
        @LocalCacheable public String number(Long value) { return "long"; }
        @LocalCacheable public String text(CharSequence value) { return "text"; }
        @LocalCacheable public String text(Serializable value) { return "serializable"; }
        public Object object(Object value) { return value; }
        public int primitive(int value) { return value; }
    }
}
