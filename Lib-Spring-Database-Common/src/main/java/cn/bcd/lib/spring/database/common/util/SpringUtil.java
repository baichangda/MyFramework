package cn.bcd.lib.spring.database.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

public class SpringUtil {
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    /**
     * 获取spring.yml中指定key的节点
     *
     * @param keys
     * @return
     * @throws IOException
     */
    public static JsonNode[] getSpringPropsInYml(String... keys) throws IOException {
        final JsonNode base = loadBaseConfig();
        if (base == null) {
            return new JsonNode[keys.length];
        }

        final JsonNode active = loadActiveConfig(base);
        JsonNode[] res = new JsonNode[keys.length];
        for (int i = 0; i < keys.length; i++) {
            res[i] = resolveKey(active, base, keys[i]);
        }
        return res;
    }

    private static JsonNode loadBaseConfig() throws IOException {
        try (InputStream is = SpringUtil.class.getResourceAsStream("/application.yml")) {
            if (is != null) {
                return YAML_MAPPER.readTree(is);
            }
        }
        return null;
    }

    private static JsonNode loadActiveConfig(JsonNode base) throws IOException {
        JsonNode profilesNode = Optional.ofNullable(base.get("spring"))
                .map(e -> e.get("profiles"))
                .map(e -> e.get("active"))
                .orElse(null);
        if (profilesNode == null) {
            return null;
        }

        String activeProfile = extractFirstProfile(profilesNode);
        if (activeProfile == null || activeProfile.isEmpty()) {
            return null;
        }

        String activeFileName = "application-" + activeProfile + ".yml";

        // 优先从 classpath 加载
        try (InputStream is = SpringUtil.class.getResourceAsStream("/" + activeFileName)) {
            if (is != null) {
                return YAML_MAPPER.readTree(is);
            }
        }
        return null;
    }

    private static String extractFirstProfile(JsonNode node) {
        if (node.isTextual()) {
            String text = node.asText();
            int comma = text.indexOf(',');
            return comma >= 0 ? text.substring(0, comma).trim() : text.trim();
        }
        if (node.isArray() && !node.isEmpty()) {
            return node.get(0).asText();
        }
        return null;
    }

    private static JsonNode resolveKey(JsonNode active, JsonNode base, String key) {
        String[] parts = key.split("\\.");

        JsonNode result = lookup(active, parts);
        if (result != null) {
            return result;
        }

        return lookup(base, parts);
    }

    private static JsonNode lookup(JsonNode root, String[] parts) {
        if (root == null) {
            return null;
        }
        JsonNode temp = root;
        for (String part : parts) {
            temp = temp.get(part);
            if (temp == null) {
                return null;
            }
        }
        return temp;
    }
}
