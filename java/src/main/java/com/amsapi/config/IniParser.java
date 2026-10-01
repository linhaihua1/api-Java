package com.amsapi.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简易 INI 解析器。
 * 行为对齐 Python configparser（interpolation=None, optionxform=str）：
 *   - 保留键的原始大小写（请求头名不能被改成小写）
 *   - 不做 % 插值（${var} 和时间戳格式不会被误解析）
 *   - 注释行以 ; 或 # 开头
 *   - 段名 [section]，键值对 key = value
 */
public class IniParser {

    private IniParser() {
    }

    /**
     * 读取 INI 文件。
     *
     * @return section -> (key -> value) 的有序映射；文件不存在返回空 Map
     */
    public static Map<String, Map<String, String>> read(Path path) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return result;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String currentSection = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    currentSection = trimmed.substring(1, trimmed.length() - 1).trim();
                    result.computeIfAbsent(currentSection, k -> new LinkedHashMap<>());
                    continue;
                }
                if (currentSection == null) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                // 去掉行内注释（; 或 # 后面的内容），但只在值不以引号包裹时
                value = stripInlineComment(value);
                result.get(currentSection).put(key, value);
            }
        } catch (IOException e) {
            throw new RuntimeException("读取 INI 文件失败: " + path, e);
        }
        return result;
    }

    private static String stripInlineComment(String value) {
        // 简单处理：值中出现 ; 或 # 且前面有空格时截断
        // 与 Python configparser 行为略有差异，但对本工程足够
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == ';' || c == '#') && i > 0 && Character.isWhitespace(value.charAt(i - 1))) {
                return value.substring(0, i).trim();
            }
        }
        return value;
    }

    /**
     * 把一个段的键统一小写（用于除 [headers] / [cookies] 之外的段）。
     */
    public static Map<String, String> lowerKeys(Map<String, String> data) {
        Map<String, String> result = new LinkedHashMap<>();
        if (data == null) {
            return result;
        }
        for (Map.Entry<String, String> e : data.entrySet()) {
            result.put(e.getKey().trim().toLowerCase(), e.getValue());
        }
        return result;
    }
}
