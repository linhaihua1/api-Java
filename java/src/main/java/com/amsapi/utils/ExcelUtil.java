package com.amsapi.utils;

import com.amsapi.common.Constants;
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

    /** Excel 用例标准列顺序（对齐 Python 版） */
    public static final String[] TEMPLATE_HEADERS = {
            Constants.FIELD_ID, Constants.FIELD_TITLE, Constants.FIELD_TAGS,
            Constants.FIELD_METHOD, Constants.FIELD_URL, Constants.FIELD_HEADERS,
            Constants.FIELD_REQUEST_BODY, Constants.FIELD_PARAMS,
            Constants.FIELD_COOKIES, Constants.FIELD_CONTENT_TYPE,
            Constants.FIELD_HOST, Constants.FIELD_EXPECTED_CODE,
            Constants.FIELD_EXPECTS, Constants.FIELD_RELATION,
            Constants.FIELD_DEPENDS, Constants.FIELD_SETUP,
            Constants.FIELD_TEARDOWN, Constants.FIELD_CHECK_BUSINESS,
            "result", "error"
    };

    private ExcelUtil() {
    }

    private static Workbook getWorkbook() throws IOException {
        File currentFile = new File(Settings.EXCEL_FILE);
        // 若文件路径变化或缓存失效，重新加载
        if (cachedWorkbook == null || cachedFile == null || !cachedFile.equals(currentFile)) {
            if (cachedWorkbook != null) {
                try { cachedWorkbook.close(); } catch (IOException ignored) {}
            }
            cachedFile = currentFile;
            if (currentFile.exists()) {
                try (FileInputStream fis = new FileInputStream(currentFile)) {
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

    /**
     * 创建标准 Excel 模板文件（含表头 + 示例行）。
     * 文件不存在时才创建，已存在则跳过。
     */
    public static synchronized void createTemplateIfAbsent() throws IOException {
        File file = new File(Settings.EXCEL_FILE);
        if (file.exists()) return;
        file.getParentFile().mkdirs();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(file)) {
            Sheet sheet = wb.createSheet(Settings.RunConfig.SHEET_NAME);
            // 表头
            Row header = sheet.createRow(0);
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(TEMPLATE_HEADERS[i]);
                c.setCellStyle(bold);
                sheet.setColumnWidth(i, 4000);
            }
            // 示例行
            Row sample = sheet.createRow(1);
            String[] sampleRow = {
                    "1001", "登录并提取票据", "smoke",
                    "get", "/v1/users/login",
                    "{\"Content-Type\":\"application/json\"}",
                    "{\"username\":\"demo_user\",\"password\":\"demo_password\"}",
                    "", "", "", "", "200",
                    "type:body.data=dict; not_empty:body.data.ticket",
                    "ticket=body.data.ticket", "", "", "", "true", "", ""
            };
            for (int i = 0; i < sampleRow.length; i++) {
                sample.createCell(i).setCellValue(sampleRow[i]);
            }
            wb.write(fos);
            logger.info("已创建 Excel 模板：{}", file.getAbsolutePath());
        }
    }

    /**
     * 按列名回写结果到指定行。
     */
    public static void writeBackByName(int rowIndex, String colName, Object value) {
        String[] headers = TEMPLATE_HEADERS;
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(colName)) {
                writeBack(rowIndex, i, value);
                return;
            }
        }
        logger.warn("回写列名不存在：{}", colName);
    }
}
