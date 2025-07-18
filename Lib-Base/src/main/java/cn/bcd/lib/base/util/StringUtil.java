package cn.bcd.lib.base.util;

import org.slf4j.helpers.MessageFormatter;

import java.util.Arrays;
import java.util.List;

public class StringUtil {

    /**
     * 将一串包含特殊字符串的换成驼峰模式
     * example:
     * a_b_c会成为aBC
     *
     * @param str
     * @param splitChar
     * @return
     */
    public static String splitCharToCamelCase(String str, char splitChar) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        char[] arr = str.toCharArray();
        boolean nextIsUpper = false;
        result.append(Character.toLowerCase(arr[0]));
        for (int i = 1; i <= arr.length - 1; i++) {
            char c = arr[i];
            if (c == splitChar) {
                nextIsUpper = true;
            } else {
                if (nextIsUpper) {
                    result.append(Character.toUpperCase(c));
                    nextIsUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }

    /**
     * 将驼峰格式字符串转换为分隔符格式
     *
     * @param str
     * @param splitChar
     * @return
     */
    public static String camelCaseToSplitChar(String str, char splitChar) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        char[] arr = str.toCharArray();
        result.append(Character.toLowerCase(str.charAt(0)));
        for (int i = 1; i <= arr.length - 1; i++) {
            char c = arr[i];
            if (Character.isUpperCase(c)) {
                result.append(splitChar);
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }

    /**
     * 将信息转换为格式化
     * 使用方式和sl4j log一样、例如
     * {@link org.slf4j.Logger#info(String, Object...)}
     * 如果需要转义、则\\{}
     *
     * @param message
     * @param params
     * @return
     */
    public static String format(String message, Object... params) {
        return MessageFormatter.arrayFormat(message, params, null).getMessage();
    }

    public static String format(String message, Object arg) {
        return MessageFormatter.format(message, arg).getMessage();
    }

    public static String format(String message, Object arg1, Object arg2) {
        return MessageFormatter.format(message, arg1, arg2).getMessage();
    }

    public static void main(String[] args) {
        System.out.println(format("{}-{}", 123, null));
        System.out.println(format("{}-\\{}-{}", 123, null, "abc"));

        List<String[]> list = List.of(new String[]{"a", "b", "c"}, new String[]{"1", "2", "3"});
        String[][] arr1 = list.toArray(new String[0][0]);
        String[][] arr2 = list.toArray(new String[0][]);
        for (String[] arr : arr1) {
            System.out.println(Arrays.toString(arr));
        }
        for (String[] arr : arr2) {
            System.out.println(Arrays.toString(arr));
        }
    }


}
