package cn.bcd.lib.spring.database.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

public class SpringUtil{
    /**
     * 获取spring.yml中指定key的节点
     *
     * @param keys
     * @return
     * @throws IOException
     */
    public static JsonNode[] getSpringPropsInYml(String... keys) throws IOException {
        YAMLMapper yamlMapper = YAMLMapper.builder().build();
        final JsonNode base = yamlMapper.readTree(ClassLoader.getSystemResourceAsStream("application.yml"));
        final JsonNode suffix = Optional.ofNullable(base.get("spring")).map(e -> e.get("profiles")).map(e -> e.get("active")).orElse(null);
        JsonNode active = null;
        if (suffix != null) {
            String activePathStr = "application-" + suffix.asText();
            if (Files.exists(Paths.get(activePathStr))) {
                active = yamlMapper.readTree(new File(activePathStr));
            }
        }
        JsonNode[] res = new JsonNode[keys.length];
        A:
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            String[] arr = key.split("\\.");
            JsonNode temp = active;
            if (active != null) {
                for (String s : arr) {
                    temp = temp.get(s);
                    if (temp == null) {
                        break;
                    }
                }
            }
            if (temp == null) {
                temp = base;
                for (String s : arr) {
                    temp = temp.get(s);
                    if (temp == null) {
                        continue A;
                    }
                }
            }
            res[i] = temp;
        }
        return res;
    }
}
