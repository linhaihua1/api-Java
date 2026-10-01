package com.amsapi.testcase;

import com.amsapi.api.BaseApi;
import com.amsapi.common.exceptions.CaseSkipped;
import com.amsapi.config.Settings;
import com.amsapi.utils.ExcelUtil;
import com.amsapi.utils.LogUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Excel 数据驱动用例。
 * 对应 Python 版 testcase/test_excel_driver.py。
 */
public class TestExcelDriver {
    private static final Logger logger = LogUtil.logger;
    private static BaseApi apiClient;

    @BeforeAll
    static void setup() {
        LogUtil.init();
        apiClient = new BaseApi();
    }

    @TestFactory
    Stream<DynamicTest> excelCases() throws Exception {
        // 数据源过滤
        String source = Settings.resolve_data_source();
        if (source != null && !source.isEmpty() && !"all".equals(source) && !"excel".equals(source)) {
            return Stream.empty();
        }

        List<Map<String, Object>> cases = ExcelUtil.readCases();
        if (cases.isEmpty()) {
            logger.warn("Excel 用例为空或未找到 sheet: {}", Settings.RunConfig.SHEET_NAME);
            return Stream.empty();
        }

        // 冒烟过滤
        List<Map<String, Object>> filtered = new ArrayList<>();
        if (Settings.RunConfig.SMOKE_ONLY) {
            for (Map<String, Object> c : cases) {
                Object tags = c.get("tags");
                if (com.amsapi.common.base.Base.matchTags(tags, Settings.RunConfig.TAGS)) {
                    filtered.add(c);
                }
            }
        } else {
            filtered = cases;
        }

        return filtered.stream().map(caze -> {
            String id = String.valueOf(caze.getOrDefault("id", "case"));
            String title = String.valueOf(caze.getOrDefault("title", caze.getOrDefault("url", "case")));
            String displayName = "id" + id + "[" + title + "]";
            return dynamicTest(displayName, () -> {
                try {
                    apiClient.runCase(caze);
                } catch (CaseSkipped e) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(false, e.getMessage());
                }
            });
        });
    }
}
