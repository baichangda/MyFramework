package cn.bcd.lib.base.util;

import org.slf4j.helpers.MessageFormatter;

import java.util.Arrays;
import java.util.List;

public class StringUtil {

    /**
     * 将分隔符格式转换为小驼峰格式，例如a_b_c转换为aBC。
     * 忽略开头、结尾及连续的分隔符，首个保留字符转为小写，后续分段首字符转为大写。
     * 其余字符转为小写，仅含分隔符时返回空字符串。
     *
     * @param str 待转换字符串，为null或空字符串时原样返回
     * @param splitChar 分隔符
     * @return 转换后的小驼峰字符串
     */
    public static String splitCharToCamelCase(String str, char splitChar) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        char[] arr = str.toCharArray();
        boolean nextIsUpper = false;
        for (char c : arr) {
            if (c == splitChar) {
                // 开头的分隔符不影响首字符大小写，后续分隔符标记下一段的起点。
                nextIsUpper = !result.isEmpty();
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
     * 使用SLF4J的占位符和转义规则格式化字符串。
     * 所有参数均作为普通占位符参数处理，包括末尾的异常对象，不提取日志异常。
     *
     * @param message 包含{}占位符的模板，为null时返回null
     * @param params 占位符参数
     * @return 格式化后的字符串
     */
    public static String format(String message, Object... params) {
        return MessageFormatter.arrayFormat(message, params, null).getMessage();
    }

    /**
     * 格式化单个参数，异常对象也按普通参数处理。
     *
     * @param message 包含{}占位符的模板，为null时返回null
     * @param arg 占位符参数
     * @return 格式化后的字符串
     */
    public static String format(String message, Object arg) {
        return format(message, new Object[]{arg});
    }

    /**
     * 格式化两个参数，末尾的异常对象也按普通参数处理。
     *
     * @param message 包含{}占位符的模板，为null时返回null
     * @param arg1 第一个占位符参数
     * @param arg2 第二个占位符参数
     * @return 格式化后的字符串
     */
    public static String format(String message, Object arg1, Object arg2) {
        return format(message, new Object[]{arg1, arg2});
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
