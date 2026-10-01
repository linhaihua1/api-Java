package com.amsapi.utils;

import com.amsapi.common.base.Base;
import com.amsapi.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * YAML 用例读取工具。
 * 对应 Python 版 utils/yamlutil.py。
 */
public class YamlUtil {
    private static final Logger logger = LoggerFactory.getLogger(YamlUtil.class);
    private static final Yaml YAML = new Yaml();

    private YamlUtil() {
    }

    /**
     * 读取 data/cases/ 下所有 YAML 用例。
     * @param tags 冒烟标签过滤；为 null 时不过滤
     * @return 用例列表，每条用例会附加 __source__ 字段标识来源文件
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> readCases(List<String> tags) {
        File dir = new File(Settings.CASES_DIR);
        List<Map<String, Object>> allCases = new ArrayList<>();
        if (!dir.isDirectory()) {
            logger.warn("YAML 用例目录不存在: {}", Settings.CASES_DIR);
            return allCases;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (files == null) return allCases;

        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file)) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                Object loaded = YAML.load(content);
                if (loaded instanceof Map) {
                    Object casesObj = ((Map<String, Object>) loaded).get("cases");
                    if (casesObj instanceof List) {
                        for (Object c : (List<Object>) casesObj) {
                            if (c instanceof Map) {
                                Map<String, Object> caseData = new LinkedHashMap<>((Map<String, Object>) c);
                                caseData.put("__source__", file.getName());
                                // 标签过滤
                                if (tags == null || tags.isEmpty() || Base.matchTags(caseData.get("tags"), tags)) {
                                    allCases.add(caseData);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("读取 YAML 文件失败: {} - {}", file.getName(), e.getMessage());
            }
        }
        return allCases;
    }

    public static List<Map<String, Object>> readCases() {
        return readCases(null);
    }
}
