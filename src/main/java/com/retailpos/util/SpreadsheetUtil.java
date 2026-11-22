package com.retailpos.util;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes simple Excel tables: one header row, then one record per row.
 * Only this class knows about Apache POI.
 */
public final class SpreadsheetUtil {

    /** The first sheet of a workbook: its header row and the data rows below it. */
    public record Table(List<String> headers, List<TableRow> rows) {
    }

    /**
     * One data row. {@code rowNumber} is the number Excel shows (header = 1).
     * Each value is a trimmed String, a BigDecimal for numeric cells, or null for an empty cell.
     */
    public record TableRow(int rowNumber, List<Object> values) {

        public Object value(int column) {
            return column >= 0 && column < values.size() ? values.get(column) : null;
        }

        public boolean isEmpty() {
            return values.stream().allMatch(v -> v == null || (v instanceof String s && s.isEmpty()));
        }
    }

    private SpreadsheetUtil() {
    }

    /**
     * Reads the first sheet. Row 1 must hold the column headers.
     * Rows after {@code maxRows} data rows are not read; the result then has {@code maxRows + 1} rows,
     * so the caller can tell the file was too long.
     *
     * @throws BusinessException if the file is not a readable Excel workbook
     */
    public static Table readFirstSheet(InputStream in, int maxRows) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                return new Table(List.of(), List.of());
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null || sheet.getPhysicalNumberOfRows() == 0) {
                return new Table(List.of(), List.of());
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Object v = valueOf(headerRow.getCell(c), formatter);
                headers.add(v == null ? "" : v.toString());
            }
            List<TableRow> rows = new ArrayList<>();
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum() && rows.size() <= maxRows; r++) {
                Row row = sheet.getRow(r);
                List<Object> values = new ArrayList<>();
                for (int c = 0; c < headers.size(); c++) {
                    values.add(row == null ? null : valueOf(row.getCell(c), formatter));
                }
                TableRow tableRow = new TableRow(r + 1, values);
                if (!tableRow.isEmpty()) {
                    rows.add(tableRow);
                }
            }
            return new Table(headers, rows);
        } catch (EncryptedDocumentException e) {
            throw new BusinessException("The file is password-protected. Save it without a password and try again.");
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("The file could not be read as an Excel workbook (.xlsx).");
        }
    }

    /**
     * Writes a workbook: the first sheet has a bold header row (nothing else), and an optional
     * second sheet holds help lines, one per row.
     */
    public static void writeTemplate(OutputStream out, String sheetName, List<String> headers,
                                     String helpSheetName, List<String> helpLines) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);

            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.max(14, headers.get(i).length() + 4) * 256);
            }
            sheet.createFreezePane(0, 1);

            if (helpSheetName != null) {
                Sheet help = workbook.createSheet(helpSheetName);
                for (int i = 0; i < helpLines.size(); i++) {
                    help.createRow(i).createCell(0).setCellValue(helpLines.get(i));
                }
                help.setColumnWidth(0, 100 * 256);
            }
            workbook.write(out);
        }
    }

    /** A cell as a trimmed String, a BigDecimal (numbers) or null (empty). Formulas give their cached result. */
    private static Object valueOf(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        switch (type) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return formatter.formatCellValue(cell).trim();
                }
                // NumberToTextConverter gives the number as Excel shows it (19.99, not 19.989999...).
                return new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue()));
            case STRING:
                String s = cell.getStringCellValue().trim();
                return s.isEmpty() ? null : s;
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue()).toUpperCase();
            default:
                return null;
        }
    }
}
