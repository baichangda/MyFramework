package cn.bcd.app.bp.backend.base.support_excel;

import cn.bcd.lib.base.exception.BaseException;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExcelUtil {

    /**
     * 判断是否是excel文件
     * 注意xlsx是zip格式、所以zip文件也被认定为xlsx格式
     * 保持输入流位置不变；非文件流必须支持 mark/reset，可在调用前包装 BufferedInputStream。
     * @param is
     * @return
     */
    public static boolean isExcel(InputStream is) {
        FileMagic magic = readFileMagic(is);
        return magic == FileMagic.OOXML || magic == FileMagic.OLE2;
    }

    /**
     * 判断是否是xlsx文件
     * 注意xlsx是zip格式、所以zip文件也被认定为xlsx格式
     * 输入流要求及位置保持行为同 {@link #isExcel(InputStream)}。
     * @param is
     * @return
     */
    public static boolean isXlsx(InputStream is) {
        return readFileMagic(is) == FileMagic.OOXML;
    }

    /** 输入流要求及位置保持行为同 {@link #isExcel(InputStream)}。 */
    public static boolean isXls(InputStream is) {
        return readFileMagic(is) == FileMagic.OLE2;
    }

    private static FileMagic readFileMagic(InputStream is) {
        Objects.requireNonNull(is, "input stream");
        // Both OLE2 and OOXML signatures fit in eight bytes.
        if (is.markSupported()) {
            is.mark(8);
            try {
                try {
                    return FileMagic.valueOf(is.readNBytes(8));
                } finally {
                    is.reset();
                }
            } catch (IOException ex) {
                return FileMagic.UNKNOWN;
            }
        }
        if (is instanceof FileInputStream file) {
            try {
                long position = file.getChannel().position();
                try {
                    return FileMagic.valueOf(is.readNBytes(8));
                } finally {
                    file.getChannel().position(position);
                }
            } catch (IOException ex) {
                return FileMagic.UNKNOWN;
            }
        }
        throw new IllegalArgumentException("Input stream must support mark/reset; wrap it in BufferedInputStream before detection and reuse that wrapper");
    }

    private record CellPlacement(int row, int col, Td td) {}

    private record TableLayout(Td[][] cells, int colNum, List<CellPlacement> placements) {}

    private static TableLayout checkRule(List<List<Td>> table) {
        if (table == null || table.size() > SpreadsheetVersion.EXCEL2007.getMaxRows()) {
            throw BaseException.get("Invalid table or too many rows");
        }
        final int size = table.size();
        int colNum = 0;
        for (int i = 0; i < size; i++) {
            if (table.get(i) == null) {
                throw BaseException.get("Null row at index {}", i);
            }
            for (Td td : table.get(i)) {
                if (td == null || td.rowSpan < 1 || td.colSpan < 1) {
                    throw BaseException.get("Invalid cell or span at row {}", i);
                }
                if (td.rowSpan > size - i || td.colSpan > SpreadsheetVersion.EXCEL2007.getMaxColumns()) {
                    throw BaseException.get("Cell span exceeds table bounds at row {}", i);
                }
                if (i == 0) {
                    if (td.colSpan > SpreadsheetVersion.EXCEL2007.getMaxColumns() - colNum) {
                        throw BaseException.get("Too many columns");
                    }
                    colNum += td.colSpan;
                }
            }
        }
        Td[][] cells = new Td[size][colNum];
        List<CellPlacement> placements = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int col = 0;
            for (Td td : table.get(i)) {
                while (col < colNum && cells[i][col] != null) {
                    col++;
                }
                if (td.colSpan > colNum - col) {
                    throw BaseException.get("Too many cells or colspan exceeds table width at row {}", i);
                }
                for (int x = i; x < i + td.rowSpan; x++) {
                    for (int y = col; y < col + td.colSpan; y++) {
                        if (cells[x][y] != null) {
                            throw BaseException.get("Overlapping cells at row {}, column {}", x, y);
                        }
                        cells[x][y] = td;
                    }
                }
                placements.add(new CellPlacement(i, col, td));
                col += td.colSpan;
            }
            for (int j = 0; j < colNum; j++) {
                if (cells[i][j] == null) {
                    throw BaseException.get("Missing cell at row {}, column {}", i, j);
                }
            }
        }
        return new TableLayout(cells, colNum, placements);
    }

    private record FontKey(short color, boolean bold) {}

    private record StyleKey(FontKey font, short background, BorderStyle borderStyle, short borderColor,
                            HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment,
                            String dataFormat) {}

    static class WriteExcelContext {
        final XSSFWorkbook workbook;

        final XSSFDataFormat dataFormat;
        final Map<StyleKey, XSSFCellStyle> cellStyleMap = new HashMap<>();
        final Map<FontKey, XSSFFont> fontMap = new HashMap<>();

        public WriteExcelContext(XSSFWorkbook workbook) {
            this.workbook = workbook;
            this.dataFormat = workbook.createDataFormat();
        }
    }

    /**
     * 模拟html table、tr、td形式写入成xlsx
     *
     * @param table
     * @param os
     */
    public static void writeExcel_xlsx(List<List<Td>> table, OutputStream os) {
        final TableLayout layout = checkRule(table);
        try (final XSSFWorkbook workbook = new XSSFWorkbook()) {
            WriteExcelContext writeExcelContext = new WriteExcelContext(workbook);
            final XSSFSheet sheet = workbook.createSheet();
            final int rowNum = layout.cells.length;
            final int colNum = layout.colNum;
            for (int i = 0; i < rowNum; i++) {
                final XSSFRow row = sheet.createRow(i);
                for (int j = 0; j < colNum; j++) {
                    final XSSFCell cell = row.createCell(j);
                    setCell(cell, layout.cells[i][j], writeExcelContext);
                }
            }
            for (CellPlacement placement : layout.placements) {
                Td td = placement.td;
                if (td.rowSpan != 1 || td.colSpan != 1) {
                    sheet.addMergedRegion(new CellRangeAddress(placement.row, placement.row + td.rowSpan - 1,
                            placement.col, placement.col + td.colSpan - 1));
                }
            }
            workbook.write(os);
        } catch (IOException ex) {
            throw BaseException.get(ex);
        }
    }

    private static void setCell(XSSFCell cell, Td td, WriteExcelContext writeExcelContext) {
        cell.setCellValue(td.content);
        FontKey fontKey = new FontKey(td.fontColor.index, td.fontBold);
        StyleKey styleKey = new StyleKey(fontKey, td.bgColor.index, td.borderStyle, td.borderColor.index,
                td.horizontalAlignment, td.verticalAlignment, td.dataFormat);
        final CellStyle cellStyle = writeExcelContext.cellStyleMap.computeIfAbsent(styleKey, k -> {
            final XSSFCellStyle xssfCellStyle = writeExcelContext.workbook.createCellStyle();
            xssfCellStyle.setFillForegroundColor(td.bgColor.index);
            xssfCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            final XSSFFont xssfFont = writeExcelContext.fontMap.computeIfAbsent(fontKey, i -> {
                final XSSFFont font = writeExcelContext.workbook.createFont();
                font.setColor(td.fontColor.index);
                font.setBold(td.fontBold);
                return font;
            });
            xssfCellStyle.setFont(xssfFont);
            xssfCellStyle.setBorderTop(td.borderStyle);
            xssfCellStyle.setTopBorderColor(td.borderColor.index);
            xssfCellStyle.setBorderRight(td.borderStyle);
            xssfCellStyle.setRightBorderColor(td.borderColor.index);
            xssfCellStyle.setBorderBottom(td.borderStyle);
            xssfCellStyle.setBottomBorderColor(td.borderColor.index);
            xssfCellStyle.setBorderLeft(td.borderStyle);
            xssfCellStyle.setLeftBorderColor(td.borderColor.index);
            xssfCellStyle.setAlignment(td.horizontalAlignment);
            xssfCellStyle.setVerticalAlignment(td.verticalAlignment);
            xssfCellStyle.setDataFormat(writeExcelContext.dataFormat.getFormat(td.dataFormat));
            return xssfCellStyle;
        });
        cell.setCellStyle(cellStyle);
    }
}
