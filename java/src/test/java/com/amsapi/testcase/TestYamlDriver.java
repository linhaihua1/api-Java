package com.amsapi.testcase;

import com.amsapi.api.BaseApi;
import com.amsapi.common.base.Base;
import com.amsapi.common.exceptions.CaseSkipped;
import com.amsapi.config.Settings;
import com.amsapi.utils.LogUtil;
import com.amsapi.utils.YamlUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * YAML 数据驱动用例。
 * 对应 Python 版 testcase/test_yaml_driver.py。
 */
public class TestYamlDriver {
    private static final Logger logger = LogUtil.logger;
    private static BaseApi apiClient;

    @BeforeAll
    static void setup() {
        LogUtil.init();
        apiClient = new BaseApi();
    }

    @TestFactory
    Stream<DynamicTest> yamlCases() {
        // 数据源过滤
        String source = Settings.resolve_data_source();
        if (source != null && !source.isEmpty() && !"all".equals(source) && !"yaml".equals(source)) {
            return Stream.empty();
        }

        List<String> tags = Settings.RunConfig.SMOKE_ONLY ? Base.parseTags(Settings.RunConfig.TAGS) : null;
        List<Map<String, Object>> cases = YamlUtil.readCases(tags);
        if (cases.isEmpty()) {
            logger.warn("YAML 用例目录为空: {}", Settings.CASES_DIR);
            return Stream.empty();
        }

        return cases.stream().map(caze -> {
            String id = String.valueOf(caze.getOrDefault("id", "case"));
            String sourceFile = String.valueOf(caze.getOrDefault("__source__", "yaml"));
            String title = String.valueOf(caze.getOrDefault("title", "case"));
            String displayName = "id" + id + "[" + sourceFile + "::" + title + "]";
            return dynamicTest(displayName, () -> {
                try {
                    apiClient.runCase(caze);
                } catch (CaseSkipped e) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(false, e.getMessage());
                } catch (Exception e) {
                    // 收集所有层级的异常消息
                    StringBuilder allMsgs = new StringBuilder();
                    Throwable t = e;
                    while (t != null) {
                        if (t.getMessage() != null) allMsgs.append(t.getMessage()).append(" | ");
                        t = t.getCause();
                    }
                    String combined = allMsgs.toString().toLowerCase();
                    // 无可用服务器 / 未配置 host / 连接失败 → 优雅跳过
                    if (combined.contains("target host")
                            || combined.contains("connection refused")
                            || combined.contains("unknownhost")
                            || combined.contains("no route")
                            || combined.contains("connect timed out")
                            || combined.contains("refused")) {
                        org.junit.jupiter.api.Assumptions.assumeTrue(false,
                                "无可用服务器，跳过: " + e.getClass().getSimpleName());
                    }
                    throw e;
                }
            });
        });
    }
}
