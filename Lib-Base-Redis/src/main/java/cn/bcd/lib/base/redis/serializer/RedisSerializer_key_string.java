package cn.bcd.lib.base.redis.serializer;

import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.Charset;

public class RedisSerializer_key_string extends StringRedisSerializer {
    public final String keyPrefix;
    public final int keyPrefixLen;

    public RedisSerializer_key_string(String keyPrefix, Charset charset) {
        super(charset);
        this.keyPrefix = keyPrefix;
        this.keyPrefixLen = keyPrefix.length();
    }

    @Override
    public String deserialize(byte[] bytes) {
        final String deserialize = super.deserialize(bytes);
        if (keyPrefixLen > 0) {
            return deserialize.substring(keyPrefixLen);
        } else {
            return deserialize;
        }
    }

    @Override
    public byte[] serialize(String str) {
        if (keyPrefixLen > 0) {
            return super.serialize(keyPrefix + str);
        } else {
            return super.serialize(str);
        }
    }
}
