package cn.bcd.lib.base.util;

import com.google.common.base.Strings;
import org.slf4j.helpers.MessageFormatter;

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

    public static boolean isChineseChar(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS // 常用汉字
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS // 兼容汉字
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A // 扩展A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B // 扩展B
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION // 中文符号
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS // 全角字符
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION; // 通用标点
    }

    /**
     * 根据给定的表名、行列值、生成控制台类似表格样式的字符串、便于打印输出
     * 字符串仅支持ascii字符
     *
     * @param name
     * @param list
     * @return
     */
    public static String tableFormat(String name, List<List<String>> list) {
        StringBuilder res = new StringBuilder();
        boolean noData = list == null || list.isEmpty() || list.getFirst().isEmpty();
        if (name == null && noData) {
            return "";
        }
        if (noData) {
            String data = "empty";
            int nameLength = name.length();
            int dataLength = data.length();
            int len = Math.max(nameLength, dataLength) + 4;
            int width = len - 2;
            int leftSpace = (width - nameLength) / 2;
            int rightSpace = width - nameLength - leftSpace;
            int dataLeftSpace = (width - dataLength) / 2;
            int dataRightSpace = width - dataLength - dataLeftSpace;
            res.append("+").append(Strings.repeat("-", width)).append("+\n");
            res.append("|").append(Strings.repeat(" ", leftSpace)).append(name).append(Strings.repeat(" ", rightSpace)).append("|\n");
            res.append("+").append(Strings.repeat("-", width)).append("+\n");
            res.append("|").append(Strings.repeat(" ", dataLeftSpace)).append(data).append(Strings.repeat(" ", dataRightSpace)).append("|\n");
            res.append("+").append(Strings.repeat("-", width)).append("+\n");
            return res.toString();
        }
        //计算列宽
        int[] colWidth = new int[list.getFirst().size()];
        for (List<String> row : list) {
            for (int i = 0; i < row.size(); i++) {
                String ele = row.get(i);
                colWidth[i] = Math.max(colWidth[i], ele.length() + 2);
            }
        }

        if (name != null) {
            int len = colWidth.length + 1;
            for (int i : colWidth) {
                len += i;
            }
            int width = len - 2;
            //根据name调整colWidth
            if (name.length() + 2 > width) {
                len = colWidth.length + 1;
                int diff = name.length() + 2 - width;
                int div = diff / colWidth.length;
                int leave = diff % colWidth.length;
                for (int i = 0; i < colWidth.length; i++) {
                    colWidth[i] += div;
                    if (i < leave) {
                        colWidth[i]++;
                    }
                    len += colWidth[i];
                }
                width = len - 2;
            }

            int leftSpace = (width - name.length()) / 2;
            int rightSpace = width - name.length() - leftSpace;
            //添加表头
            res.append("+").append(Strings.repeat("-", width)).append("+\n");
            res.append("|").append(Strings.repeat(" ", leftSpace)).append(name).append(Strings.repeat(" ", rightSpace)).append("|\n");
        }

        StringBuilder rowLine = new StringBuilder();
        for (int i : colWidth) {
            rowLine.append("+");
            rowLine.append(Strings.repeat("-", i));
        }
        rowLine.append("+\n");
        for (List<String> row : list) {
            res.append(rowLine);
            StringBuilder rowSb = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                rowSb.append("|");
                int width = colWidth[i];
                String ele = row.get(i);
                //计算当前值前后间距、如果前后间距不等、则左间距为奇数
                int leftSpace = (width - ele.length()) / 2;
                int rightSpace = width - ele.length() - leftSpace;
                //拼接字符串
                String colSb = Strings.repeat(" ", leftSpace) +
                        ele +
                        Strings.repeat(" ", rightSpace);
                rowSb.append(colSb);
            }
            rowSb.append("|\n");
            res.append(rowSb);
        }
        res.append(rowLine);
        return res.toString();
    }

    public static void main(String[] args) {
        System.out.println(format("{}-{}", 123, null));
        System.out.println(format("{}-\\{}-{}", 123, null, "abc"));

        String s = tableFormat("a", List.of(
//                List.of("1", "2", "3"),
//                List.of("122223", "456", "789"),
//                List.of("abc", "d5555555555ef", "ghi"),
//                List.of("jkl", "mno", "pqr"),
//                List.of("abc", "d5555555555ef", "ghi")
        ));
        System.out.println(s);
    }


}
