package cn.bcd.lib.base.json;

import cn.bcd.lib.base.exception.BaseException;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.type.TypeFactory;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.Map;

/**
 * Json工具类
 */
public final class JsonUtil {

    public static final JsonMapper OBJECT_MAPPER = buildObjectMapper();

    public static final ObjectWriter OBJECT_WRITER_PRETTY =
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter();

    private JsonUtil() {
    }

    public static JavaType getJavaType(Type type) {
        return TypeFactory.createDefaultInstance().constructType(type);
    }

    /**
     * Jackson 3 推荐使用 Builder 创建不可变 JsonMapper。
     */
    public static JsonMapper buildObjectMapper() {
        return withConfig(JsonMapper.builder()).build();
    }

    /**
     * Jackson 3 中 ObjectMapper/JsonMapper 不再推荐创建后修改，
     * 所以这里改成配置 Builder。
     */
    public static JsonMapper.Builder withConfig(JsonMapper.Builder builder) {
        SimpleModule simpleModule = new SimpleModule();

        // 设置所有 Number 属性输出为字符串
        simpleModule.addSerializer(Number.class, ToStringSerializer.instance);

        // 设置 byte[] 序列化为数字数组，默认是 base64 字符串
        simpleModule.addSerializer(byte[].class, new ValueSerializer<byte[]>() {
            @Override
            public void serialize(byte[] value,
                                  JsonGenerator gen,
                                  SerializationContext serializers) {
                gen.writeStartArray();
                for (byte b : value) {
                    gen.writeNumber(b);
                }
                gen.writeEndArray();
            }
        });

        return builder
                .addModule(simpleModule)

                // 忽略 null 属性输出
                .changeDefaultPropertyInclusion(v ->
                        v.withOverrides(JsonInclude.Value.ALL_NON_NULL)
                )

                // 解析 JSON 字符串为实体类时，忽略多余属性
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

                // 序列化时遇到空 Bean 不抛异常
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

                // Jackson 3 中日期相关特性迁移到了 DateTimeFeature
                // Jackson 3 默认不再输出时间戳，这里显式保持你原来的行为：输出时间戳
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)

                // 时间戳使用毫秒，不使用纳秒
                .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    }

    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JacksonException e) {
            throw BaseException.get(e);
        }
    }

    public static String toJsonPretty(Object object) {
        try {
            return OBJECT_WRITER_PRETTY.writeValueAsString(object);
        } catch (JacksonException e) {
            throw BaseException.get(e);
        }
    }

    public static byte[] toJsonAsBytes(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JacksonException e) {
            throw BaseException.get(e);
        }
    }

    static class TestBean{
        public Date d=new Date();
        public Map<String,String> m;
        public int a;
        public byte[] br=new byte[]{1,2,3};
    }

    static void main() {
        TestBean testBean=new TestBean();
        String json = JsonUtil.toJson(testBean);
        System.out.println(json);
        TestBean testBean1=JsonUtil.OBJECT_MAPPER.readValue(json,TestBean.class);
        System.out.println(testBean1);
    }
}