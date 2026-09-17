package cn.bcd.lib.spring.database.jdbc.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 读取 classpath 下 application.yml/application.yaml 及其 profile 文件的轻量工具。
 *
 * <p>该工具不替代 Spring Boot 的完整配置加载机制。Spring 容器内应优先使用
 * {@code Environment} 或 {@code Binder}；这里主要用于 Spring 容器启动前的简单读取。</p>
 */
public final class ApplicationYamlUtil {
    private static final String ACTIVE_PROFILES_PROPERTY = "spring.profiles.active";
    private static final String ACTIVE_PROFILES_ENV = "SPRING_PROFILES_ACTIVE";
    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

    private ApplicationYamlUtil() {
    }

    /**
     * 按 key 读取 classpath YAML 配置。后声明的 active profile 优先级更高。
     *
     * @param keys 使用点号分隔的配置路径
     * @return 与 keys 顺序一致的节点数组；不存在的配置返回 null
     * @throws IOException YAML 读取失败
     */
    public static JsonNode[] getSpringPropsInYml(String... keys) throws IOException {
        Objects.requireNonNull(keys, "keys");
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key must not be blank");
            }
        }

        JsonNode base = loadConfig("application");
        if (base == null) {
            return new JsonNode[keys.length];
        }

        List<JsonNode> profileConfigs = new ArrayList<>();
        for (String profile : findActiveProfiles(base)) {
            JsonNode profileConfig = loadConfig("application-" + profile);
            if (profileConfig != null) {
                profileConfigs.add(profileConfig);
            }
        }

        JsonNode[] result = new JsonNode[keys.length];
        for (int i = 0; i < keys.length; i++) {
            result[i] = resolveKey(profileConfigs, base, keys[i]);
        }
        return result;
    }

    private static JsonNode loadConfig(String fileStem) throws IOException {
        for (String extension : List.of(".yml", ".yaml")) {
            try (InputStream input = ApplicationYamlUtil.class.getResourceAsStream("/" + fileStem + extension)) {
                if (input != null) {
                    return YAML_MAPPER.readTree(input);
                }
            }
        }
        return null;
    }

    private static List<String> findActiveProfiles(JsonNode base) {
        String configured = System.getProperty(ACTIVE_PROFILES_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ACTIVE_PROFILES_ENV);
        }
        if (configured != null && !configured.isBlank()) {
            return splitProfiles(configured);
        }

        JsonNode profilesNode = base.path("spring").path("profiles").path("active");
        if (profilesNode.isString()) {
            return splitProfiles(profilesNode.stringValue());
        }
        if (profilesNode.isArray()) {
            List<String> profiles = new ArrayList<>();
            profilesNode.forEach(e -> profiles.addAll(splitProfiles(e.stringValue())));
            return profiles;
        }
        return List.of();
    }

    private static List<String> splitProfiles(String profiles) {
        return Arrays.stream(profiles.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .distinct()
                .toList();
    }

    private static JsonNode resolveKey(List<JsonNode> profileConfigs, JsonNode base, String key) {
        String[] parts = key.split("\\.");
        for (int i = profileConfigs.size() - 1; i >= 0; i--) {
            JsonNode result = lookup(profileConfigs.get(i), parts);
            if (result != null) {
                return result;
            }
        }
        return lookup(base, parts);
    }

    private static JsonNode lookup(JsonNode root, String[] parts) {
        JsonNode current = root;
        for (String part : parts) {
            current = current.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
