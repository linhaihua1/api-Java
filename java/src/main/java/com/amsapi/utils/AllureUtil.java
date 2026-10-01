package com.amsapi.utils;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Allure 报告封装。
 * 对应 Python 版 utils/allureutil.py。
 */
public final class AllureUtil {
    private static final Logger logger = LoggerFactory.getLogger(AllureUtil.class);
    public static final boolean ALLURE_AVAILABLE = true;

    private AllureUtil() {
    }

    public static void attachRequest(String method, String url, Map<String, String> headers,
                                     Object body, Object params) {
        StringBuilder sb = new StringBuilder();
        sb.append("Method: ").append(method).append("\n");
        sb.append("URL: ").append(url).append("\n");
        if (headers != null && !headers.isEmpty()) {
            sb.append("Headers: ").append(headers).append("\n");
        }
        if (params != null) {
            sb.append("Params: ").append(params).append("\n");
        }
        if (body != null) {
            sb.append("Body: ").append(body);
        }
        attach("Request", sb.toString());
    }

    @SuppressWarnings("unchecked")
    public static void attachResponse(Map<String, Object> response) {
        if (response == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(response.get("code")).append("\n");
        sb.append("Elapsed: ").append(response.get("elapsed")).append(" ms\n");
        sb.append("Body: ").append(response.get("body"));
        attach("Response", sb.toString());
    }

    private static void attach(String name, String content) {
        try {
            Allure.addAttachment(name, "text/plain",
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), ".txt");
        } catch (Exception e) {
            // Allure 不可用时静默降级
        }
    }

    public static void step(String name, Runnable action) {
        Allure.step(name, () -> {
            action.run();
            return null;
        });
    }

    public static void writeEnvironment(String resultsDir, Map<String, String> envInfo) {
        // Allure Java 通过 allure-environment.properties 或环境变量设置
        // 这里简化处理，环境信息由测试框架自动采集
        logger.info("Allure 环境信息: {}", envInfo);
    }
}
