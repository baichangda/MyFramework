package cn.bcd.app.mqtt.server.topic;

/**
 * MQTT 主题名与主题过滤器的校验、匹配工具。
 *
 * <p>实现包含 MQTT 对 {@code $} 系统主题的特殊规则：以通配符开头的过滤器不会
 * 匹配系统主题。</p>
 */
public final class MqttTopicFilter {

    private static final String MULTI_LEVEL_WILDCARD = "#";
    private static final String SINGLE_LEVEL_WILDCARD = "+";

    /** 工具类不允许实例化。 */
    private MqttTopicFilter() {
    }

    /**
     * 判断字符串是否为合法的主题过滤器。
     *
     * @param topicFilter 待校验的主题过滤器
     */
    public static boolean isValid(String topicFilter) {
        if (topicFilter == null || topicFilter.isEmpty() || topicFilter.indexOf('\0') >= 0) {
            return false;
        }
        String[] levels = levels(topicFilter);
        for (int index = 0; index < levels.length; index++) {
            String level = levels[index];
            if (level.indexOf('#') >= 0
                    && (!MULTI_LEVEL_WILDCARD.equals(level) || index != levels.length - 1)) {
                return false;
            }
            if (level.indexOf('+') >= 0 && !SINGLE_LEVEL_WILDCARD.equals(level)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断主题过滤器是否匹配指定主题名。
     *
     * @param topicFilter 主题过滤器
     * @param topicName 主题名
     */
    public static boolean matches(String topicFilter, String topicName) {
        if (!isValid(topicFilter) || !isValidTopicName(topicName)) {
            return false;
        }
        if (topicName.charAt(0) == '$'
                && (topicFilter.charAt(0) == '#' || topicFilter.charAt(0) == '+')) {
            return false;
        }

        String[] filterLevels = levels(topicFilter);
        String[] topicLevels = levels(topicName);
        int filterIndex = 0;
        int topicIndex = 0;
        while (filterIndex < filterLevels.length) {
            String filterLevel = filterLevels[filterIndex];
            if (MULTI_LEVEL_WILDCARD.equals(filterLevel)) {
                return true;
            }
            if (topicIndex >= topicLevels.length) {
                return false;
            }
            if (!SINGLE_LEVEL_WILDCARD.equals(filterLevel)
                    && !filterLevel.equals(topicLevels[topicIndex])) {
                return false;
            }
            filterIndex++;
            topicIndex++;
        }
        return topicIndex == topicLevels.length;
    }

    /**
     * 判断授权过滤器是否完整覆盖请求过滤器可能匹配的所有主题。
     *
     * @param allowedFilter 授权过滤器
     * @param requestedFilter 客户端请求的过滤器
     */
    public static boolean covers(String allowedFilter, String requestedFilter) {
        if (!isValid(allowedFilter) || !isValid(requestedFilter)) {
            return false;
        }
        if (requestedFilter.charAt(0) == '$'
                && (allowedFilter.charAt(0) == '#'
                || allowedFilter.charAt(0) == '+')) {
            return false;
        }

        String[] allowedLevels = levels(allowedFilter);
        String[] requestedLevels = levels(requestedFilter);
        int index = 0;
        while (index < allowedLevels.length) {
            String allowedLevel = allowedLevels[index];
            if (MULTI_LEVEL_WILDCARD.equals(allowedLevel)) {
                return true;
            }
            if (index >= requestedLevels.length) {
                return false;
            }
            String requestedLevel = requestedLevels[index];
            if (MULTI_LEVEL_WILDCARD.equals(requestedLevel)) {
                return false;
            }
            if (!SINGLE_LEVEL_WILDCARD.equals(allowedLevel)
                    && (SINGLE_LEVEL_WILDCARD.equals(requestedLevel)
                    || !allowedLevel.equals(requestedLevel))) {
                return false;
            }
            index++;
        }
        return index == requestedLevels.length;
    }

    /**
     * 判断字符串是否为不含通配符的合法主题名。
     *
     * @param topicName 待校验的主题名
     */
    public static boolean isValidTopicName(String topicName) {
        return topicName != null
                && !topicName.isEmpty()
                && topicName.indexOf('\0') < 0
                && topicName.indexOf('#') < 0
                && topicName.indexOf('+') < 0;
    }

    /**
     * 按主题层级分隔符切分字符串，并保留空层级。
     *
     * @param value 主题名或过滤器
     */
    private static String[] levels(String value) {
        return value.split("/", -1);
    }
}
