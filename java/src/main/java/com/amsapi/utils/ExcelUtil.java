package com.amsapi.utils;

import com.amsapi.config.Settings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Excel 用例读写工具。
 * 对应 Python 版 utils/excelutil.py。
 * 工作簿缓存 + 延迟落盘。
 */
public class ExcelUtil {
    private static final Logger logger = LoggerFactory.getLogger(ExcelUtil.class);
    private static Workbook cachedWorkbook;
    private static File cachedFile;
    private static final Map<Integer, Map<Integer, Object>> pendingWrites = new LinkedHashMap<>();

    private ExcelUtil() {
    }

    private static Workbook getWorkbook() throws IOException {
        if (cachedWorkbook == null) {
            File file = new File(Settings.EXCEL_FILE);
            cachedFile = file;
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    cachedWorkbook = new XSSFWorkbook(fis);
                }
            } else {
                cachedWorkbook = new XSSFWorkbook();
            }
        }
        return cachedWorkbook;
    }

    /**
     * 读取指定 sheet 的所有用例数据。
     * 第一行为表头，后续每行为一条用例（dict：列名 -> 值）。
     */
    public static List<Map<String, Object>> readCases() throws IOException {
        return readCases(Settings.RunConfig.SHEET_NAME);
    }

    public static List<Map<String, Object>> readCases(String sheetName) throws IOException {
        Workbook wb = getWorkbook();
        Sheet sheet = wb.getSheet(sheetName);
        if (sheet == null) {
            logger.warn("Excel 中未找到 sheet: {}", sheetName);
            return Collections.emptyList();
        }

        List<Map<String, Object>> cases = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return cases;

        List<String> headers = new ArrayList<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            headers.add(cell != null ? cell.getStringCellValue().trim() : "col" + i);
        }

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Object> caseData = new LinkedHashMap<>();
            boolean hasData = false;
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = row.getCell(c);
                Object value = cell != null ? getCellValue(cell) : null;
                caseData.put(headers.get(c), value);
                if (value != null && !String.valueOf(value).isEmpty()) hasData = true;
            }
            if (hasData) cases.add(caseData);
        }
        return cases;
    }

    private static Object getCellValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue();
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) return (long) d;
                return d;
            case BOOLEAN: return cell.getBooleanCellValue();
            case FORMULA:
                try { return cell.getStringCellValue(); }
                catch (Exception e) { return cell.getNumericCellValue(); }
            default: return null;
        }
    }

    /**
     * 回写执行结果（延迟落盘，flush 时统一写入）。
     */
    public static void writeBack(int rowIndex, int colIndex, Object value) {
        pendingWrites.computeIfAbsent(rowIndex, k -> new LinkedHashMap<>()).put(colIndex, value);
    }

    /**
     * 把缓存的回写结果统一落盘。
     */
    public static synchronized void flush() {
        if (pendingWrites.isEmpty() || cachedWorkbook == null) return;
        try {
            for (Map.Entry<Integer, Map<Integer, Object>> rowEntry : pendingWrites.entrySet()) {
                Sheet sheet = cachedWorkbook.getSheet(Settings.RunConfig.SHEET_NAME);
                if (sheet == null) continue;
                Row row = sheet.getRow(rowEntry.getKey());
                if (row == null) row = sheet.createRow(rowEntry.getKey());
                for (Map.Entry<Integer, Object> cellEntry : rowEntry.getValue().entrySet()) {
                    Cell cell = row.getCell(cellEntry.getKey());
                    if (cell == null) cell = row.createCell(cellEntry.getKey());
                    Object val = cellEntry.getValue();
                    if (val instanceof Number) cell.setCellValue(((Number) val).doubleValue());
                    else if (val instanceof Boolean) cell.setCellValue((Boolean) val);
                    else cell.setCellValue(String.valueOf(val));
                }
            }
            if (cachedFile != null) {
                try (FileOutputStream fos = new FileOutputStream(cachedFile)) {
                    cachedWorkbook.write(fos);
                }
            }
            pendingWrites.clear();
        } catch (IOException e) {
            logger.error("Excel 回写失败: {}", e.getMessage());
        }
    }

    public static void close() {
        flush();
        if (cachedWorkbook != null) {
            try { cachedWorkbook.close(); } catch (IOException ignored) {}
            cachedWorkbook = null;
        }
    }
}
