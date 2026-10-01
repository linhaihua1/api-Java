package com.amsapi.tests;

import com.amsapi.config.Settings;
import com.amsapi.utils.ExcelUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Excel 用例读写端到端测试。
 * 直接使用 Settings.EXCEL_FILE 路径，测试前备份、测试后恢复。
 */
public class TestExcelUtil {

    private File backupFile;
    private File realFile;

    @BeforeEach
    void setup() throws Exception {
        realFile = new File(Settings.EXCEL_FILE);
        backupFile = File.createTempFile("excel_backup", ".xlsx");
        if (realFile.exists()) {
            java.nio.file.Files.copy(realFile.toPath(), backupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        // 清理缓存
        resetExcelCache();
    }

    @AfterEach
    void teardown() throws Exception {
        resetExcelCache();
        if (backupFile.exists() && backupFile.length() > 0) {
            realFile.getParentFile().mkdirs();
            java.nio.file.Files.copy(backupFile.toPath(), realFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else if (realFile.exists()) {
            realFile.delete();
        }
        backupFile.delete();
    }

    @Test
    void testTemplateHeaders() {
        String[] headers = ExcelUtil.TEMPLATE_HEADERS;
        assertTrue(headers.length >= 18);
        assertEquals("id", headers[0]);
        assertEquals("title", headers[1]);
        assertEquals("method", headers[3]);
        assertEquals("url", headers[4]);
        assertEquals("result", headers[headers.length - 2]);
        assertEquals("error", headers[headers.length - 1]);
    }

    @Test
    void testReadCasesAndWriteBack() throws Exception {
        // 1. 创建测试 Excel
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(realFile)) {
            Sheet sheet = wb.createSheet("test_case_list");
            Row header = sheet.createRow(0);
            for (int i = 0; i < ExcelUtil.TEMPLATE_HEADERS.length; i++) {
                header.createCell(i).setCellValue(ExcelUtil.TEMPLATE_HEADERS[i]);
            }
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("1001");
            r1.createCell(1).setCellValue("登录测试");
            r1.createCell(2).setCellValue("smoke");
            r1.createCell(3).setCellValue("get");
            r1.createCell(4).setCellValue("/v1/users/login");
            r1.createCell(5).setCellValue("{\"Content-Type\":\"application/json\"}");
            r1.createCell(6).setCellValue("{\"username\":\"test\"}");
            r1.createCell(11).setCellValue(200);
            r1.createCell(13).setCellValue("ticket=body.data.ticket");
            Row r2 = sheet.createRow(2); // 空行应跳过
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("1002");
            r3.createCell(1).setCellValue("登出测试");
            r3.createCell(3).setCellValue("post");
            r3.createCell(4).setCellValue("/v1/users/logout");
            wb.write(fos);
        }
        resetExcelCache();

        // 2. 读取
        List<Map<String, Object>> cases = ExcelUtil.readCases();
        assertEquals(2, cases.size(), "应读取到 2 条用例（空行跳过）");

        // 3. 验证字段
        Map<String, Object> c1 = cases.get(0);
        assertEquals("1001", String.valueOf(c1.get("id")));
        assertEquals("登录测试", c1.get("title"));
        assertEquals("get", c1.get("method"));
        assertEquals("/v1/users/login", c1.get("url"));
        assertEquals("200", String.valueOf(c1.get("expected_code")));
        assertEquals("ticket=body.data.ticket", c1.get("relation"));
        assertNotNull(c1.get("headers"));
        assertTrue(String.valueOf(c1.get("headers")).contains("Content-Type"));

        Map<String, Object> c2 = cases.get(1);
        assertEquals("1002", String.valueOf(c2.get("id")));
        assertEquals("post", c2.get("method"));

        // 4. 回写
        ExcelUtil.writeBackByName(1, "result", "pass");
        ExcelUtil.writeBackByName(3, "result", "fail");
        ExcelUtil.writeBackByName(3, "error", "Connection refused");
        ExcelUtil.flush();

        // 5. 验证回写
        try (Workbook verify = new XSSFWorkbook(new FileInputStream(realFile))) {
            Sheet sheet = verify.getSheet("test_case_list");
            assertEquals("pass", sheet.getRow(1).getCell(18).getStringCellValue());
            assertEquals("fail", sheet.getRow(3).getCell(18).getStringCellValue());
            assertEquals("Connection refused", sheet.getRow(3).getCell(19).getStringCellValue());
        }
    }

    @Test
    void testEmptySheetReturnsEmpty() throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(realFile)) {
            wb.createSheet("test_case_list");
            wb.write(fos);
        }
        resetExcelCache();
        List<Map<String, Object>> cases = ExcelUtil.readCases();
        assertTrue(cases.isEmpty());
    }

    @Test
    void testMissingSheetReturnsEmpty() throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(realFile)) {
            wb.createSheet("other_sheet");
            wb.write(fos);
        }
        resetExcelCache();
        List<Map<String, Object>> cases = ExcelUtil.readCases("test_case_list");
        assertTrue(cases.isEmpty());
    }

    @Test
    void testCreateTemplateIfAbsent() throws Exception {
        if (realFile.exists()) realFile.delete();
        resetExcelCache();
        ExcelUtil.createTemplateIfAbsent();
        assertTrue(realFile.exists());
        resetExcelCache();
        List<Map<String, Object>> cases = ExcelUtil.readCases();
        // 模板含 1 行示例数据
        assertEquals(1, cases.size());
        assertEquals("1001", String.valueOf(cases.get(0).get("id")));
    }

    private void resetExcelCache() throws Exception {
        Field wb = ExcelUtil.class.getDeclaredField("cachedWorkbook");
        wb.setAccessible(true);
        Object old = wb.get(null);
        if (old != null) ((Workbook) old).close();
        wb.set(null, null);
        Field cf = ExcelUtil.class.getDeclaredField("cachedFile");
        cf.setAccessible(true);
        cf.set(null, null);
        Field pw = ExcelUtil.class.getDeclaredField("pendingWrites");
        pw.setAccessible(true);
        ((Map<?, ?>) pw.get(null)).clear();
    }
}
