package cn.bcd.lib.base.json;

import cn.bcd.lib.base.exception.BaseException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by Administrator on 2017/5/12.
 */
public class JsonUtil {
    public final static ObjectMapper OBJECT_MAPPER = withConfig(new ObjectMapper());

    public static JavaType getJavaType(Type type) {
        //1、判断是否带有泛型
        if (type instanceof ParameterizedType) {
            Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
            //1.1、获取泛型类型
            Class<?> rawClass = (Class<?>) ((ParameterizedType) type).getRawType();
            JavaType[] javaTypes = new JavaType[actualTypeArguments.length];
            for (int i = 0; i < actualTypeArguments.length; i++) {
                //1.2、泛型也可能带有泛型，递归获取
                javaTypes[i] = getJavaType(actualTypeArguments[i]);
            }
            return TypeFactory.defaultInstance().constructParametricType(rawClass, javaTypes);
        } else {
            //2、简单类型直接用该类构建JavaType
            Class<?> cla = (Class<?>) type;
            return TypeFactory.defaultInstance().constructParametricType(cla, new JavaType[0]);
        }
    }


    /**
     * 不会生成新的ObjectMapper,只会改变当前传入的ObjectMapper
     *
     * @param t
     * @return
     */
    public static <T extends ObjectMapper> T withConfig(T t) {
        SimpleModule simpleModule = new SimpleModule();
        //设置所有Number属性的 输出为字符串
        simpleModule.addSerializer(Number.class, ToStringSerializer.instance);
        //设置byte[]序列化、默认为base64字符串
        simpleModule.addSerializer(byte[].class, new JsonSerializer<>() {
            @Override
            public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeStartArray();
                for (byte b : value) {
                    gen.writeNumber(b);
                }
                gen.writeEndArray();
            }
        });
        t.registerModule(simpleModule);
        //注册jdk8时间类型
        t.registerModule(new JavaTimeModule());
        //设置忽略null属性输出
        t.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        //设置在解析json字符串为实体类时候,忽略多余的属性
        t.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //设置在序列化时候遇到空属性对象时候,不抛出异常
        t.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        //序列化日期时候转换为时间戳
        t.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        t.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        t.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        //序列化和反序列化的数据内容中添加类属性
        //旧版本
        //t.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        //新版本
        //t.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return t;
    }

    /**
     * 此方法会调用 withConfig 改变objectMapper
     *
     * @param object
     * @return
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw BaseException.get(e);
        }
    }

    public static byte[] toJsonAsBytes(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw BaseException.get(e);
        }
    }

}

