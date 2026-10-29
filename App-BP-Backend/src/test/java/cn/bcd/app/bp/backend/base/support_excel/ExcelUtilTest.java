package cn.bcd.app.bp.backend.base.support_excel;

import cn.bcd.lib.base.exception.BaseException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ExcelUtilTest {
    private static final List<Predicate<InputStream>> DETECTORS = List.of(ExcelUtil::isExcel, ExcelUtil::isXlsx, ExcelUtil::isXls);

    @Test
    void allStylePropertiesArePreservedIndependently() throws Exception {
        List<Consumer<Td>> variations = List.of(td -> {}, td -> td.fontBold = true,
                td -> td.fontColor = IndexedColors.BLUE, td -> td.bgColor = IndexedColors.YELLOW,
                td -> td.borderStyle = BorderStyle.DASHED, td -> td.borderColor = IndexedColors.RED,
                td -> td.horizontalAlignment = HorizontalAlignment.LEFT,
                td -> td.verticalAlignment = VerticalAlignment.TOP, td -> td.dataFormat = "0.00");
        List<Td> row = new ArrayList<>();
        for (Consumer<Td> variation : variations) {
            Td td = Td.newTd("content");
            variation.accept(td);
            row.add(td);
        }
        try (var workbook = read(List.of(row))) {
            for (int i = 0; i < row.size(); i++) {
                assertStyle(row.get(i), workbook.getSheetAt(0).getRow(0).getCell(i).getCellStyle(), workbook);
            }
        }
    }

    @Test
    void boldFontsAreIndependentOfBackgroundAndIdenticalStylesAreReused() throws Exception {
        Td normal = Td.newTd("normal");
        Td bold = Td.newTd("bold");
        bold.fontBold = true;
        bold.bgColor = IndexedColors.YELLOW;
        Td boldWhite = Td.newTd("bold white");
        boldWhite.fontBold = true;
        Td same = Td.newTd("same style");
        try (var workbook = read(List.of(List.of(normal, bold, boldWhite, same)))) {
            var row = workbook.getSheetAt(0).getRow(0);
            for (int i = 0; i < 4; i++) {
                assertStyle(List.of(normal, bold, boldWhite, same).get(i), row.getCell(i).getCellStyle(), workbook);
            }
            assertEquals(row.getCell(0).getCellStyle().getIndex(), row.getCell(3).getCellStyle().getIndex());
            assertEquals(row.getCell(1).getCellStyle().getFontIndex(), row.getCell(2).getCellStyle().getFontIndex());
        }
    }

    @Test
    void detectsXlsxWithoutConsumingTheStreamAndItCanStillBeParsed() throws Exception {
        byte[] original = write(List.of(List.of(Td.newTd("content"))));
        try (var input = new BufferedInputStream(new ByteArrayInputStream(original))) {
            assertTrue(ExcelUtil.isExcel(input));
            assertTrue(ExcelUtil.isXlsx(input));
            assertFalse(ExcelUtil.isXls(input));
            try (var workbook = new XSSFWorkbook(input)) {
                assertEquals("content", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            }
        }
    }

    @Test
    void detectsXlsWithoutConsumingTheStream() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var workbook = new HSSFWorkbook()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("xls");
            workbook.write(output);
        }
        try (var input = new ByteArrayInputStream(output.toByteArray())) {
            assertTrue(ExcelUtil.isExcel(input));
            assertTrue(ExcelUtil.isXls(input));
            assertFalse(ExcelUtil.isXlsx(input));
            assertArrayEquals(output.toByteArray(), input.readAllBytes());
        }
    }

    @Test
    void rejectsUnmarkableStreamsBeforeReadingAndAcceptsAnExternalWrapper() throws Exception {
        byte[] original = write(List.of(List.of(Td.newTd("content"))));
        for (Predicate<InputStream> detector : DETECTORS) {
            try (InputStream input = unmarkable(original)) {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> detector.test(input));
                assertTrue(error.getMessage().contains("BufferedInputStream"));
                assertArrayEquals(original, input.readAllBytes());
            }
        }
        try (var input = new BufferedInputStream(unmarkable(original))) {
            assertTrue(ExcelUtil.isXlsx(input));
            assertArrayEquals(original, input.readAllBytes());
        }
    }

    @Test
    void fileInputStreamPositionIsRestoredIncludingNonzeroOffsets() throws Exception {
        byte[] original = write(List.of(List.of(Td.newTd("content"))));
        Path file = Files.createTempFile(Path.of("build"), "excel-detection-", ".xlsx");
        try {
            var bytes = new ByteArrayOutputStream();
            bytes.write(new byte[]{1, 2, 3});
            bytes.write(original);
            Files.write(file, bytes.toByteArray());
            for (Predicate<InputStream> detector : DETECTORS) {
                try (var input = new FileInputStream(file.toFile())) {
                    input.skipNBytes(3);
                    assertEquals(detector != DETECTORS.get(2), detector.test(input));
                    assertEquals(3, input.getChannel().position());
                    assertArrayEquals(original, input.readAllBytes());
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void emptyShortAndNonExcelStreamsReturnFalse() throws Exception {
        for (byte[] bytes : List.of(new byte[0], new byte[]{1, 2}, "plain text".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            for (Predicate<InputStream> detector : DETECTORS) {
                try (var input = new ByteArrayInputStream(bytes)) {
                    assertFalse(detector.test(input));
                    assertArrayEquals(bytes, input.readAllBytes());
                }
            }
        }
    }

    @Test
    void ioFailureReturnsFalseAndStillResetsWithoutClosing() {
        for (Predicate<InputStream> detector : DETECTORS) {
            class FailingStream extends InputStream {
                int position;
                boolean closed;
                @Override public boolean markSupported() { return true; }
                @Override public void mark(int limit) {}
                @Override public void reset() { position = 0; }
                @Override public int read() throws IOException { position++; throw new IOException("failure"); }
                @Override public void close() { closed = true; }
            }
            FailingStream input = new FailingStream();
            assertFalse(detector.test(input));
            assertEquals(0, input.position);
            assertFalse(input.closed);
        }
    }

    @Test
    void writesValidMixedRowAndColumnSpansIncludingFullyCoveredRows() throws Exception {
        Td merged = Td.newTd("merged", 2, 2);
        merged.fontBold = true;
        List<List<Td>> table = List.of(List.of(merged, Td.newTd("right", 2, 1)), List.of(),
                List.of(Td.newTd("bottom", 1, 2), Td.newTd("last")));
        try (var workbook = read(table)) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(3, sheet.getNumMergedRegions());
            assertTrue(sheet.getMergedRegions().contains(new CellRangeAddress(0, 1, 0, 1)));
            assertTrue(sheet.getMergedRegions().contains(new CellRangeAddress(0, 1, 2, 2)));
            assertTrue(sheet.getMergedRegions().contains(new CellRangeAddress(2, 2, 0, 1)));
            assertEquals("last", sheet.getRow(2).getCell(2).getStringCellValue());
            assertStyle(merged, sheet.getRow(1).getCell(1).getCellStyle(), workbook);
        }
    }

    @Test
    void rejectsOutOfBoundsAndOverlappingMergesBeforeWriting() {
        assertInvalid(List.of(List.of(Td.newTd("first", 1, 2)), List.of(Td.newTd("last", 2, 1))));
        assertInvalid(List.of(List.of(Td.newTd("left"), Td.newTd("down", 2, 1), Td.newTd("right")),
                List.of(Td.newTd("crosses occupied cell", 1, 2), Td.newTd("extra"))));
        assertInvalid(List.of(List.of(Td.newTd("first"), Td.newTd("second")), List.of(Td.newTd("wide", 1, 3))));
    }

    @Test
    void rejectsZeroNegativeAndOverflowingSpansEvenOnExtraCells() {
        for (int invalid : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertInvalid(List.of(List.of(Td.newTd("first")), List.of(Td.newTd("second"), Td.newTd("bad", invalid, 1))));
            assertInvalid(List.of(List.of(Td.newTd("first")), List.of(Td.newTd("second"), Td.newTd("bad", 1, invalid))));
        }
        assertInvalid(List.of(List.of(Td.newTd("bad", Integer.MAX_VALUE, 1))));
        assertInvalid(List.of(List.of(Td.newTd("bad", 1, Integer.MAX_VALUE))));
        assertInvalid(List.of(List.of(Td.newTd("max", 1, 16_384), Td.newTd("too many"))));
    }

    @Test
    void rejectsMissingExtraAndNullCellsOrRows() {
        assertInvalid(List.of(List.of(Td.newTd("a"), Td.newTd("b")), List.of(Td.newTd("missing"))));
        assertInvalid(List.of(List.of(Td.newTd("a")), List.of(Td.newTd("b"), Td.newTd("extra"))));
        assertInvalid(Arrays.asList((List<Td>) null));
        assertInvalid(List.of(Arrays.asList((Td) null)));
        assertInvalid(null);
    }

    @Test
    void emptyTableProducesAnEmptyWorkbook() throws Exception {
        try (var workbook = read(List.of())) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals(0, workbook.getSheetAt(0).getPhysicalNumberOfRows());
        }
    }

    private static void assertStyle(Td expected, CellStyle actual, XSSFWorkbook workbook) {
        var font = workbook.getFontAt(actual.getFontIndex());
        assertEquals(expected.fontColor.index, font.getColor());
        assertEquals(expected.fontBold, font.getBold());
        assertEquals(expected.bgColor.index, actual.getFillForegroundColor());
        assertEquals(FillPatternType.SOLID_FOREGROUND, actual.getFillPattern());
        assertEquals(expected.horizontalAlignment, actual.getAlignment());
        assertEquals(expected.verticalAlignment, actual.getVerticalAlignment());
        assertEquals(expected.dataFormat, actual.getDataFormatString());
        assertEquals(expected.borderStyle, actual.getBorderTop());
        assertEquals(expected.borderStyle, actual.getBorderBottom());
        assertEquals(expected.borderStyle, actual.getBorderLeft());
        assertEquals(expected.borderStyle, actual.getBorderRight());
        assertEquals(expected.borderColor.index, actual.getTopBorderColor());
        assertEquals(expected.borderColor.index, actual.getBottomBorderColor());
        assertEquals(expected.borderColor.index, actual.getLeftBorderColor());
        assertEquals(expected.borderColor.index, actual.getRightBorderColor());
    }

    private static void assertInvalid(List<List<Td>> table) {
        var output = new ByteArrayOutputStream();
        assertThrows(BaseException.class, () -> ExcelUtil.writeExcel_xlsx(table, output));
        assertEquals(0, output.size());
    }

    private static InputStream unmarkable(byte[] bytes) {
        return new FilterInputStream(new ByteArrayInputStream(bytes)) {
            @Override public boolean markSupported() { return false; }
        };
    }

    private static byte[] write(List<List<Td>> table) {
        var output = new ByteArrayOutputStream();
        ExcelUtil.writeExcel_xlsx(table, output);
        return output.toByteArray();
    }

    private static XSSFWorkbook read(List<List<Td>> table) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(write(table)));
    }
}
