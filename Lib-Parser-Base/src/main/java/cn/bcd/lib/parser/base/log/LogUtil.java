package cn.bcd.lib.parser.base.log;

import cn.bcd.lib.parser.base.log.BitBuf_reader_log;
import cn.bcd.lib.parser.base.log.BitBuf_writer_log;
import cn.bcd.lib.parser.base.util.ParseUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class LogUtil {
    public static final ConcurrentHashMap<Class<?>, HashMap<String, Integer>> class_fieldName_lineNo =
            new ConcurrentHashMap<>();

    public static String formatLogValue(Object value) {
        if (value != null && value.getClass().isArray()) {
            return ParseUtil.format("{}", value);
        }
        return "[" + value + "]";
    }

    public static String formatBitLogs(BitBuf_reader_log.Log[] logs) {
        return java.util.Arrays.stream(logs).map(BitBuf_reader_log.Log::msg)
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    public static String formatBitLogs(BitBuf_writer_log.Log[] logs) {
        return java.util.Arrays.stream(logs).map(BitBuf_writer_log.Log::msg)
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    public static String getFieldLocation(Class<?> clazz, String fieldName) {
        try {
            return getFieldStackTrace(clazz.getField(fieldName).getDeclaringClass(), fieldName);
        } catch (NoSuchFieldException e) {
            return getFieldStackTrace(clazz, fieldName);
        }
    }

    public static String getFieldStackTrace(Class<?> clazz, String fieldName) {
        HashMap<String, Integer> lineNumbers = class_fieldName_lineNo.computeIfAbsent(clazz, LogUtil::findLineNumbers);
        Integer lineNo = lineNumbers.get(fieldName == null ? "class" : fieldName);
        if (lineNo == null) {
            return "";
        }
        return "(" + clazz.getNestHost().getSimpleName() + ".java:" + lineNo + ")";
    }

    private static HashMap<String, Integer> findLineNumbers(Class<?> clazz) {
        HashMap<String, Integer> result = new HashMap<>();
        Path source = findSource(clazz.getNestHost());
        if (source == null) {
            return result;
        }

        Map<String, Pattern> fieldPatterns = new HashMap<>();
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            fieldPatterns.put(field.getName(), Pattern.compile("\\b" + Pattern.quote(field.getName()) + "\\b\\s*(?:=|;)"));
        }
        Pattern classPattern = Pattern.compile("\\b(class|record|interface|enum)\\s+" + Pattern.quote(clazz.getSimpleName()) + "\\b");

        try (BufferedReader reader = Files.newBufferedReader(source)) {
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                if (!result.containsKey("class") && classPattern.matcher(line).find()) {
                    result.put("class", lineNo);
                }
                for (Map.Entry<String, Pattern> entry : fieldPatterns.entrySet()) {
                    if (!result.containsKey(entry.getKey()) && entry.getValue().matcher(line).find()) {
                        result.put(entry.getKey(), lineNo);
                    }
                }
                lineNo++;
            }
        } catch (IOException ignored) {
            // Source locations are optional debugging information and must never break parsing.
        }
        return result;
    }

    private static Path findSource(Class<?> topClass) {
        String classFile = topClass.getName().replace('.', '/') + ".java";
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        for (String sourceSet : new String[]{"main", "test"}) {
            Path relative = Paths.get("src", sourceSet, "java").resolve(classFile);
            Path direct = workingDirectory.resolve(relative);
            if (Files.isRegularFile(direct)) {
                return direct;
            }
            try (DirectoryStream<Path> children = Files.newDirectoryStream(workingDirectory)) {
                for (Path child : children) {
                    Path candidate = child.resolve(relative);
                    if (Files.isRegularFile(candidate)) {
                        return candidate;
                    }
                }
            } catch (IOException ignored) {
                return null;
            }
        }
        return null;
    }
}
