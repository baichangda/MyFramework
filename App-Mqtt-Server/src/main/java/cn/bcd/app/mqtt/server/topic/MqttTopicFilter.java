package cn.bcd.app.mqtt.server.topic;

public final class MqttTopicFilter {

    private static final String MULTI_LEVEL_WILDCARD = "#";
    private static final String SINGLE_LEVEL_WILDCARD = "+";

    private MqttTopicFilter() {
    }

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

    public static boolean isValidTopicName(String topicName) {
        return topicName != null
                && !topicName.isEmpty()
                && topicName.indexOf('\0') < 0
                && topicName.indexOf('#') < 0
                && topicName.indexOf('+') < 0;
    }

    private static String[] levels(String value) {
        return value.split("/", -1);
    }
}
