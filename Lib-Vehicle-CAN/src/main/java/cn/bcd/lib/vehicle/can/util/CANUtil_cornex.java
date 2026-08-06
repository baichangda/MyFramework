package cn.bcd.lib.vehicle.can.util;

import org.apache.fesod.sheet.ExcelReader;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.read.metadata.ReadSheet;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CAN/CAN FD 通信矩阵读取工具。
 * 第 0-5 列为报文属性，第 6-29 列为信号属性，第 34 列起为各 ECU 节点的 Tx/Rx 收发方向。</p>
 */
public final class CANUtil_cornex {
    private static final int MESSAGE_NAME = 0;
    private static final int MESSAGE_TYPE = 1;
    private static final int MESSAGE_ID = 2;
    private static final int MESSAGE_SEND_TYPE = 3;
    private static final int MESSAGE_CYCLE_TIME = 4;
    private static final int MESSAGE_LENGTH = 5;
    private static final int SIGNAL_NAME = 6;
    private static final int SIGNAL_MULTIPLEXING_VALUE = 7;
    private static final int SIGNAL_GROUP_NAME = 8;
    private static final int E2E_PROFILE = 9;
    private static final int E2E_DATA_ID = 10;
    private static final int ASIL = 11;
    private static final int SIGNAL_DESCRIPTION = 12;
    private static final int BYTE_ORDER = 13;
    private static final int START_BYTE = 14;
    private static final int START_BIT = 15;
    private static final int SIGNAL_SEND_TYPE = 16;
    private static final int SIGNAL_LENGTH = 17;
    private static final int DATA_TYPE = 18;
    private static final int RESOLUTION = 19;
    private static final int OFFSET = 20;
    private static final int PHYSICAL_MIN = 21;
    private static final int PHYSICAL_MAX = 22;
    private static final int HEX_MIN = 23;
    private static final int HEX_MAX = 24;
    private static final int INITIAL_VALUE = 25;
    private static final int INVALID_VALUE = 26;
    private static final int INACTIVE_VALUE = 27;
    private static final int UNIT = 28;
    private static final int VALUE_DESCRIPTION = 29;
    private static final int MESSAGE_FAST_CYCLE_TIME = 30;
    private static final int MESSAGE_REPETITION_COUNT = 31;
    private static final int MESSAGE_DELAY_TIME = 32;
    private static final int REMARK = 33;
    private static final int NODE_START = 34;

    private CANUtil_cornex() {
    }

    /**
     * 解析工作簿中所有表头符合 CAN 通信矩阵结构的工作表。
     */
    public static CanWorkbook parse(File file) {
        requireReadableFile(file);
        List<String> sheetNames = findSheetNames(file);
        List<CanSheet> sheets = new ArrayList<>();
        for (String sheetName : sheetNames) {
            List<Map<Integer, String>> rows = readRows(file, sheetName);
            int headerIndex = findHeaderIndex(rows);
            if (headerIndex >= 0) {
                sheets.add(parseRows(sheetName, rows, headerIndex));
            }
        }
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("No CAN communication matrix sheet found in: " + file);
        }
        return new CanWorkbook(file.getName(), sheets);
    }

    /**
     * 按工作表名称解析一个 CAN 通信矩阵。
     */
    public static CanSheet parse(File file, String sheetName) {
        requireReadableFile(file);
        return parseRows(sheetName, readRows(file, requireSheetName(sheetName)));
    }

    /**
     * 从输入流中按工作表名称解析 CAN 通信矩阵，解析完成后不会关闭调用方传入的输入流。
     */
    public static CanSheet parse(InputStream inputStream, String sheetName) {
        Objects.requireNonNull(inputStream, "inputStream");
        String actualSheetName = requireSheetName(sheetName);
        List<Map<Integer, String>> rows = FesodSheet.read(inputStream)
                .autoCloseStream(false)
                .headRowNumber(0)
                .sheet(actualSheetName)
                .doReadSync();
        return parseRows(actualSheetName, rows);
    }

    private static List<String> findSheetNames(File file) {
        try (ExcelReader reader = FesodSheet.read(file).headRowNumber(0).build()) {
            return reader.excelExecutor().sheetList().stream()
                    .map(ReadSheet::getSheetName)
                    .toList();
        }
    }

    private static List<Map<Integer, String>> readRows(File file, String sheetName) {
        return FesodSheet.read(file)
                .headRowNumber(0)
                .sheet(sheetName)
                .doReadSync();
    }

    private static CanSheet parseRows(String sheetName, List<Map<Integer, String>> rows) {
        int headerIndex = findHeaderIndex(rows);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("Sheet '" + sheetName + "' is not a CAN communication matrix");
        }
        return parseRows(sheetName, rows, headerIndex);
    }

    private static CanSheet parseRows(String sheetName, List<Map<Integer, String>> rows, int headerIndex) {
        Map<Integer, String> header = rows.get(headerIndex);
        LinkedHashMap<Integer, String> nodeColumns = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : header.entrySet()) {
            if (entry.getKey() >= NODE_START && hasText(entry.getValue())) {
                nodeColumns.put(entry.getKey(), entry.getValue().trim());
            }
        }

        List<CanMessage> messages = new ArrayList<>();
        MutableMessage current = null;
        for (int index = headerIndex + 1; index < rows.size(); index++) {
            Map<Integer, String> row = rows.get(index);
            String messageName = text(row, MESSAGE_NAME);
            String signalName = text(row, SIGNAL_NAME);
            if (messageName == null && signalName == null) {
                continue;
            }
            int excelRow = index + 1;
            if (messageName != null) {
                if (current != null) {
                    messages.add(current.build());
                }
                current = parseMessage(row, nodeColumns, excelRow);
            }
            if (signalName != null) {
                if (current == null) {
                    throw new IllegalArgumentException("Signal '" + signalName + "' at row " + excelRow
                            + " has no preceding message in sheet '" + sheetName + "'");
                }
                current.signals.add(parseSignal(row, nodeColumns, excelRow));
            }
        }
        if (current != null) {
            messages.add(current.build());
        }
        return new CanSheet(sheetName, new ArrayList<>(nodeColumns.values()), messages);
    }

    private static MutableMessage parseMessage(Map<Integer, String> row,
                                               Map<Integer, String> nodeColumns,
                                               int excelRow) {
        String idText = text(row, MESSAGE_ID);
        if (idText == null) {
            throw new IllegalArgumentException("Message ID is empty at row " + excelRow);
        }
        MutableMessage result = new MutableMessage();
        result.name = text(row, MESSAGE_NAME);
        result.type = text(row, MESSAGE_TYPE);
        result.idText = idText;
        result.id = parseCanId(idText, excelRow);
        result.sendType = text(row, MESSAGE_SEND_TYPE);
        result.cycleTimeMs = integer(row, MESSAGE_CYCLE_TIME, excelRow);
        result.lengthBytes = integer(row, MESSAGE_LENGTH, excelRow);
        result.fastCycleTimeMs = integer(row, MESSAGE_FAST_CYCLE_TIME, excelRow);
        result.repetitionCount = integer(row, MESSAGE_REPETITION_COUNT, excelRow);
        result.delayTimeMs = integer(row, MESSAGE_DELAY_TIME, excelRow);
        result.remark = text(row, REMARK);
        result.nodeDirections = nodeDirections(row, nodeColumns);
        return result;
    }

    private static CanSignal parseSignal(Map<Integer, String> row,
                                         Map<Integer, String> nodeColumns,
                                         int excelRow) {
        return new CanSignal(
                text(row, SIGNAL_NAME),
                text(row, SIGNAL_MULTIPLEXING_VALUE),
                text(row, SIGNAL_GROUP_NAME),
                text(row, E2E_PROFILE),
                text(row, E2E_DATA_ID),
                text(row, ASIL),
                text(row, SIGNAL_DESCRIPTION),
                text(row, BYTE_ORDER),
                integer(row, START_BYTE, excelRow),
                integer(row, START_BIT, excelRow),
                text(row, SIGNAL_SEND_TYPE),
                integer(row, SIGNAL_LENGTH, excelRow),
                text(row, DATA_TYPE),
                decimal(row, RESOLUTION, excelRow),
                decimal(row, OFFSET, excelRow),
                decimal(row, PHYSICAL_MIN, excelRow),
                decimal(row, PHYSICAL_MAX, excelRow),
                text(row, HEX_MIN),
                text(row, HEX_MAX),
                text(row, INITIAL_VALUE),
                text(row, INVALID_VALUE),
                text(row, INACTIVE_VALUE),
                text(row, UNIT),
                text(row, VALUE_DESCRIPTION),
                text(row, REMARK),
                nodeDirections(row, nodeColumns));
    }

    private static Map<String, NodeDirection> nodeDirections(Map<Integer, String> row,
                                                              Map<Integer, String> nodeColumns) {
        LinkedHashMap<String, NodeDirection> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : nodeColumns.entrySet()) {
            String direction = text(row, entry.getKey());
            if (direction != null) {
                result.put(entry.getValue(), NodeDirection.fromExcelValue(direction));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static int findHeaderIndex(List<Map<Integer, String>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            Map<Integer, String> row = rows.get(i);
            if (startsWith(text(row, MESSAGE_NAME), "Msg Name")
                    && startsWith(text(row, MESSAGE_ID), "Msg ID")
                    && startsWith(text(row, SIGNAL_NAME), "Signal Name")) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static long parseCanId(String value, int excelRow) {
        try {
            String normalized = value.trim();
            int radix = 10;
            if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
                radix = 16;
                normalized = normalized.substring(2);
            }
            long id = Long.parseLong(normalized, radix);
            if (id < 0 || id > 0x1FFFFFFFL) {
                throw new IllegalArgumentException("CAN ID out of 29-bit range at row " + excelRow + ": " + value);
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CAN ID at row " + excelRow + ": " + value, e);
        }
    }

    private static Integer integer(Map<Integer, String> row, int column, int excelRow) {
        BigDecimal value = decimal(row, column, excelRow);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Expected integer at row " + excelRow + ", column "
                    + (column + 1) + ": " + value, e);
        }
    }

    private static BigDecimal decimal(Map<Integer, String> row, int column, int excelRow) {
        String value = text(row, column);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected number at row " + excelRow + ", column "
                    + (column + 1) + ": " + value, e);
        }
    }

    private static String text(Map<Integer, String> row, int column) {
        String value = row.get(column);
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String requireSheetName(String sheetName) {
        if (!hasText(sheetName)) {
            throw new IllegalArgumentException("sheetName must not be blank");
        }
        return sheetName.trim();
    }

    private static void requireReadableFile(File file) {
        Objects.requireNonNull(file, "file");
        if (!file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Excel file is not readable: " + file);
        }
    }

    /**
     * ECU 节点相对于报文或信号的收发方向。
     */
    public enum NodeDirection {
        /** 发送。 */
        TX,
        /** 接收。 */
        RX;

        private static NodeDirection fromExcelValue(String value) {
            for (NodeDirection direction : values()) {
                if (direction.name().equalsIgnoreCase(value)) {
                    return direction;
                }
            }
            throw new IllegalArgumentException("Unsupported CAN node direction: " + value);
        }
    }

    public static final class CanWorkbook {
        public final String name;
        public final List<CanSheet> sheets;

        private CanWorkbook(String name, List<CanSheet> sheets) {
            this.name = name;
            this.sheets = List.copyOf(sheets);
        }
    }

    public static final class CanSheet {
        public final String name;
        public final List<String> nodes;
        public final List<CanMessage> messages;

        private CanSheet(String name, List<String> nodes, List<CanMessage> messages) {
            this.name = name;
            this.nodes = List.copyOf(nodes);
            this.messages = List.copyOf(messages);
        }
    }

    public static final class CanMessage {
        public final String name;
        public final String type;
        public final String idText;
        public final long id;
        public final String sendType;
        public final Integer cycleTimeMs;
        public final Integer lengthBytes;
        public final Integer fastCycleTimeMs;
        public final Integer repetitionCount;
        public final Integer delayTimeMs;
        public final String remark;
        public final Map<String, NodeDirection> nodeDirections;
        public final List<CanSignal> signals;

        private CanMessage(MutableMessage source) {
            this.name = source.name;
            this.type = source.type;
            this.idText = source.idText;
            this.id = source.id;
            this.sendType = source.sendType;
            this.cycleTimeMs = source.cycleTimeMs;
            this.lengthBytes = source.lengthBytes;
            this.fastCycleTimeMs = source.fastCycleTimeMs;
            this.repetitionCount = source.repetitionCount;
            this.delayTimeMs = source.delayTimeMs;
            this.remark = source.remark;
            this.nodeDirections = source.nodeDirections;
            this.signals = List.copyOf(source.signals);
        }
    }

    public static final class CanSignal {
        /** 信号名称。 */
        public final String name;
        /** 复用信号的开关值。 */
        public final String multiplexingValue;
        /** 信号组名称。 */
        public final String groupName;
        /** E2E 保护配置。 */
        public final String e2eProfile;
        /** E2E Data ID。 */
        public final String e2eDataId;
        /** 功能安全等级要求。 */
        public final String asil;
        /** 信号描述。 */
        public final String description;
        /** 字节序，例如 Intel 或 Motorola MSB。 */
        public final String byteOrder;
        /** 起始字节，下标从 0 开始。 */
        public final Integer startByte;
        /** 通信矩阵中定义的起始位。 */
        public final Integer startBit;
        /** 信号发送类型。 */
        public final String sendType;
        /** 信号占用的位数。 */
        public final Integer lengthBits;
        /** 信号数据类型。 */
        public final String dataType;
        /** 物理值换算精度（Factor）。 */
        public final BigDecimal resolution;
        /** 物理值换算偏移量（Offset）。 */
        public final BigDecimal offset;
        /** 物理最小值。 */
        public final BigDecimal physicalMin;
        /** 物理最大值。 */
        public final BigDecimal physicalMax;
        /** 总线原始最小值，保留 Excel 中的十六进制文本。 */
        public final String hexMin;
        /** 总线原始最大值，保留 Excel 中的十六进制文本。 */
        public final String hexMax;
        /** 初始值，保留 Excel 中的十六进制文本。 */
        public final String initialValue;
        /** 无效值，保留 Excel 中的十六进制文本。 */
        public final String invalidValue;
        /** 非使能值，保留 Excel 中的十六进制文本。 */
        public final String inactiveValue;
        /** 物理单位。 */
        public final String unit;
        /** 枚举值或取值范围说明。 */
        public final String valueDescription;
        /** 备注。 */
        public final String remark;
        /** ECU 节点名称与 Tx/Rx 收发方向的对应关系。 */
        public final Map<String, NodeDirection> nodeDirections;

        private CanSignal(String name, String multiplexingValue, String groupName, String e2eProfile,
                          String e2eDataId, String asil, String description, String byteOrder,
                          Integer startByte, Integer startBit, String sendType, Integer lengthBits,
                          String dataType, BigDecimal resolution, BigDecimal offset,
                          BigDecimal physicalMin, BigDecimal physicalMax, String hexMin, String hexMax,
                          String initialValue, String invalidValue, String inactiveValue, String unit,
                          String valueDescription, String remark, Map<String, NodeDirection> nodeDirections) {
            this.name = name;
            this.multiplexingValue = multiplexingValue;
            this.groupName = groupName;
            this.e2eProfile = e2eProfile;
            this.e2eDataId = e2eDataId;
            this.asil = asil;
            this.description = description;
            this.byteOrder = byteOrder;
            this.startByte = startByte;
            this.startBit = startBit;
            this.sendType = sendType;
            this.lengthBits = lengthBits;
            this.dataType = dataType;
            this.resolution = resolution;
            this.offset = offset;
            this.physicalMin = physicalMin;
            this.physicalMax = physicalMax;
            this.hexMin = hexMin;
            this.hexMax = hexMax;
            this.initialValue = initialValue;
            this.invalidValue = invalidValue;
            this.inactiveValue = inactiveValue;
            this.unit = unit;
            this.valueDescription = valueDescription;
            this.remark = remark;
            this.nodeDirections = nodeDirections;
        }
    }

    private static final class MutableMessage {
        private String name;
        private String type;
        private String idText;
        private long id;
        private String sendType;
        private Integer cycleTimeMs;
        private Integer lengthBytes;
        private Integer fastCycleTimeMs;
        private Integer repetitionCount;
        private Integer delayTimeMs;
        private String remark;
        private Map<String, NodeDirection> nodeDirections;
        private final List<CanSignal> signals = new ArrayList<>();

        private CanMessage build() {
            return new CanMessage(this);
        }
    }
}
