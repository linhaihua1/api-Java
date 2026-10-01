package com.amsapi.common.asserts;

import com.amsapi.common.base.Base;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 断言增强层。
 * 对应 Python 版 common/asserts.py。
 *
 * 提供 21 种按路径断言的函数，路径以「完整响应」为根。
 * 也是用例 expects 字段的执行引擎。
 */
public final class Asserts {

    private Asserts() {
    }

    // ======================================================================
    // 一、取值与消息
    // ======================================================================
    public static Object valueOf(Map<String, Object> response, String path, Object defaultVal) {
        if (response == null) return defaultVal;
        if (path == null || path.isEmpty()) return response.get("body");
        Object value = Base.parseRelation(Arrays.asList(path.split("\\.")), response);
        return value == null ? defaultVal : value;
    }

    public static String summary(Map<String, Object> response) {
        return summary(response, 200);
    }

    public static String summary(Map<String, Object> response, int limit) {
        if (response == null) return "(无响应)";
        String text = "HTTP " + response.get("code") + " | body=" + response.get("body");
        return text.length() <= limit ? text : text.substring(0, limit) + "...(已截断)";
    }

    private static void fail(String message, Map<String, Object> response, String path) {
        StringBuilder sb = new StringBuilder(message);
        if (path != null) {
            sb.append("\n字段路径：").append(path.isEmpty() ? "(整个响应体)" : path);
        }
        if (response != null) {
            sb.append("\n响应摘要：").append(summary(response));
        }
        throw new AssertionError(sb.toString());
    }

    private static double toNumber(Object value, Map<String, Object> response, String path, String opName) {
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            fail(opName + " 断言需要数值，但字段实际值为 " + value + "（类型 " +
                    (value == null ? "null" : value.getClass().getSimpleName()) + "）", response, path);
            return 0;
        }
    }

    // ======================================================================
    // 二、宽松相等
    // ======================================================================
    public static boolean baseEquals(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == null && expected == null;
        }
        String left = String.valueOf(actual).trim();
        String right = String.valueOf(expected).trim();
        if (left.equalsIgnoreCase(right)) return true;
        try {
            return Double.parseDouble(left) == Double.parseDouble(right);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ======================================================================
    // 三、21 种断言算子
    // ======================================================================
    public static boolean equals(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (!baseEquals(actual, expected)) {
            fail(msg + "相等断言失败：期望 " + expected + "，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean notEquals(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (baseEquals(actual, expected)) {
            fail(msg + "不相等断言失败：期望不等于 " + expected + "，但实际就是 " + actual, response, path);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean contains(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (actual == null) {
            fail(msg + "包含断言失败：字段不存在或为 None", response, path);
        }
        boolean ok;
        if (actual instanceof Collection) {
            ok = false;
            for (Object item : (Collection<Object>) actual) {
                if (baseEquals(item, expected)) { ok = true; break; }
            }
        } else if (actual instanceof Map) {
            ok = ((Map<String, Object>) actual).containsKey(String.valueOf(expected));
        } else {
            ok = String.valueOf(actual).contains(String.valueOf(expected));
        }
        if (!ok) {
            fail(msg + "包含断言失败：期望包含 " + expected + "，实际 " + actual, response, path);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static boolean notContains(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        boolean ok;
        if (actual instanceof Collection) {
            ok = true;
            for (Object item : (Collection<Object>) actual) {
                if (baseEquals(item, expected)) { ok = false; break; }
            }
        } else if (actual instanceof Map) {
            ok = !((Map<String, Object>) actual).containsKey(String.valueOf(expected));
        } else {
            ok = !String.valueOf(actual == null ? "" : actual).contains(String.valueOf(expected));
        }
        if (!ok) {
            fail(msg + "不包含断言失败：实际 " + actual + " 中出现了 " + expected, response, path);
        }
        return true;
    }

    public static boolean startsWith(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (!String.valueOf(actual == null ? "" : actual).startsWith(String.valueOf(expected))) {
            fail(msg + "前缀断言失败：期望以 " + expected + " 开头，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean endsWith(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (!String.valueOf(actual == null ? "" : actual).endsWith(String.valueOf(expected))) {
            fail(msg + "后缀断言失败：期望以 " + expected + " 结尾，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean regex(Map<String, Object> response, String path, Object pattern, String msg) {
        Object actual = valueOf(response, path, null);
        if (!Pattern.compile(String.valueOf(pattern)).matcher(String.valueOf(actual == null ? "" : actual)).find()) {
            fail(msg + "正则断言失败：期望匹配 " + pattern + "，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean exists(Map<String, Object> response, String path, String msg) {
        if (valueOf(response, path, null) == null) {
            fail(msg + "存在性断言失败：字段不存在或为 None", response, path);
        }
        return true;
    }

    public static boolean notExists(Map<String, Object> response, String path, String msg) {
        if (valueOf(response, path, null) != null) {
            fail(msg + "存在性断言失败：期望字段不存在，实际有值 " + valueOf(response, path, null), response, path);
        }
        return true;
    }

    private static final Map<String, java.util.function.Predicate<Object>> TYPE_CHECKS = new LinkedHashMap<>();
    static {
        TYPE_CHECKS.put("str", v -> v instanceof String);
        TYPE_CHECKS.put("string", v -> v instanceof String);
        TYPE_CHECKS.put("int", v -> v instanceof Integer || v instanceof Long);
        TYPE_CHECKS.put("float", v -> v instanceof Float || v instanceof Double);
        TYPE_CHECKS.put("number", v -> v instanceof Number);
        TYPE_CHECKS.put("bool", v -> v instanceof Boolean);
        TYPE_CHECKS.put("boolean", v -> v instanceof Boolean);
        TYPE_CHECKS.put("list", v -> v instanceof List);
        TYPE_CHECKS.put("array", v -> v instanceof List);
        TYPE_CHECKS.put("dict", v -> v instanceof Map);
        TYPE_CHECKS.put("object", v -> v instanceof Map);
        TYPE_CHECKS.put("none", v -> v == null);
        TYPE_CHECKS.put("null", v -> v == null);
    }

    public static boolean typeIs(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        java.util.function.Predicate<Object> checker = TYPE_CHECKS.get(String.valueOf(expected).trim().toLowerCase());
        if (checker == null) {
            fail("不支持的类型名 " + expected + "，可选：" + String.join(", ", TYPE_CHECKS.keySet()), response, path);
        }
        if (checker.test(actual)) return true;
        // 数字字符串兜底
        String expType = String.valueOf(expected).trim().toLowerCase();
        if ((expType.equals("number") || expType.equals("int") || expType.equals("float"))
                && actual instanceof String && ((String) actual).trim().matches("-?\\d+(\\.\\d+)?")) {
            return true;
        }
        fail(msg + "类型断言失败：期望 " + expected + "，实际 " + actual +
                "（" + (actual == null ? "null" : actual.getClass().getSimpleName()) + "）", response, path);
        return false;
    }

    public static boolean in(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        List<String> allowed;
        if (expected instanceof Collection) {
            allowed = new ArrayList<>();
            for (Object o : (Collection<?>) expected) allowed.add(String.valueOf(o));
        } else {
            allowed = new ArrayList<>();
            for (String item : String.valueOf(expected).split(",")) {
                if (!item.trim().isEmpty()) allowed.add(item.trim());
            }
        }
        for (String a : allowed) {
            if (baseEquals(actual, a)) return true;
        }
        fail(msg + "集合断言失败：期望取值属于 " + allowed + "，实际 " + actual, response, path);
        return false;
    }

    public static boolean notIn(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        List<String> allowed;
        if (expected instanceof Collection) {
            allowed = new ArrayList<>();
            for (Object o : (Collection<?>) expected) allowed.add(String.valueOf(o));
        } else {
            allowed = new ArrayList<>();
            for (String item : String.valueOf(expected).split(",")) {
                if (!item.trim().isEmpty()) allowed.add(item.trim());
            }
        }
        for (String a : allowed) {
            if (baseEquals(actual, a)) {
                fail(msg + "集合断言失败：期望取值不属于 " + allowed + "，实际 " + actual, response, path);
            }
        }
        return true;
    }

    public static boolean gt(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (!(toNumber(actual, response, path, "大于") > toNumber(expected, response, path, "大于"))) {
            fail(msg + "大于断言失败：期望 > " + expected + "，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean ge(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (!(toNumber(actual, response, path, "大于等于") >= toNumber(expected, response, path, "大于等于"))) {
            fail(msg + "大于等于断言失败：期望 >= " + expected + "，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean lt(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (!(toNumber(actual, response, path, "小于") < toNumber(expected, response, path, "小于"))) {
            fail(msg + "小于断言失败：期望 < " + expected + "，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean le(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        if (!(toNumber(actual, response, path, "小于等于") <= toNumber(expected, response, path, "小于等于"))) {
            fail(msg + "小于等于断言失败：期望 <= " + expected + "，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean length(Map<String, Object> response, String path, Object expected, String msg) {
        Object actual = valueOf(response, path, null);
        int actualLen;
        try {
            if (actual instanceof String) actualLen = ((String) actual).length();
            else if (actual instanceof Collection) actualLen = ((Collection<?>) actual).size();
            else if (actual instanceof Map) actualLen = ((Map<?, ?>) actual).size();
            else throw new RuntimeException();
        } catch (Exception e) {
            fail(msg + "长度断言需要可求长度的值，实际 " + actual, response, path);
            return false;
        }
        if (!baseEquals(actualLen, expected)) {
            fail(msg + "长度断言失败：期望 " + expected + "，实际 " + actualLen + "（值=" + actual + "）", response, path);
        }
        return true;
    }

    public static boolean empty(Map<String, Object> response, String path, String msg) {
        Object actual = valueOf(response, path, null);
        boolean isEmpty = actual == null || "".equals(actual)
                || (actual instanceof Collection && ((Collection<?>) actual).isEmpty())
                || (actual instanceof Map && ((Map<?, ?>) actual).isEmpty());
        if (!isEmpty) {
            fail(msg + "空值断言失败：期望为空，实际 " + actual, response, path);
        }
        return true;
    }

    public static boolean notEmpty(Map<String, Object> response, String path, String msg) {
        Object actual = valueOf(response, path, null);
        boolean isEmpty = actual == null || "".equals(actual)
                || (actual instanceof Collection && ((Collection<?>) actual).isEmpty())
                || (actual instanceof Map && ((Map<?, ?>) actual).isEmpty());
        if (isEmpty) {
            fail(msg + "非空断言失败：字段为空", response, path);
        }
        return true;
    }

    public static boolean elapsed(Map<String, Object> response, String path, Object expected, String msg) {
        Object limit = expected != null ? expected : path;
        Object actual = response == null ? null : response.get("elapsed");
        if (actual == null) {
            fail(msg + "耗时断言失败：响应中没有 elapsed 字段", response, null);
        }
        if (Double.parseDouble(String.valueOf(actual)) > Double.parseDouble(String.valueOf(limit))) {
            fail(msg + "耗时断言失败：期望不超过 " + limit + " ms，实际 " + actual + " ms", response, null);
        }
        return true;
    }

    // ======================================================================
    // 四、算子调度
    // ======================================================================
    public static boolean runOp(String name, Map<String, Object> response, String path, Object value, String msg) {
        String key = name.trim().toLowerCase();
        switch (key) {
            case "equals": case "eq":
                return equals(response, path, value, msg);
            case "not_equals": case "ne":
                return notEquals(response, path, value, msg);
            case "contains": case "has":
                return contains(response, path, value, msg);
            case "not_contains": case "not_has":
                return notContains(response, path, value, msg);
            case "startswith":
                return startsWith(response, path, value, msg);
            case "endswith":
                return endsWith(response, path, value, msg);
            case "regex": case "match":
                return regex(response, path, value, msg);
            case "type": case "type_is":
                return typeIs(response, path, value, msg);
            case "exists": case "present":
                return exists(response, path, msg);
            case "not_exists": case "absent":
                return notExists(response, path, msg);
            case "in":
                return in(response, path, value, msg);
            case "not_in":
                return notIn(response, path, value, msg);
            case "gt":
                return gt(response, path, value, msg);
            case "ge":
                return ge(response, path, value, msg);
            case "lt":
                return lt(response, path, value, msg);
            case "le":
                return le(response, path, value, msg);
            case "length": case "len":
                return length(response, path, value, msg);
            case "empty":
                return empty(response, path, msg);
            case "not_empty": case "notempty":
                return notEmpty(response, path, msg);
            case "elapsed":
                return elapsed(response, path, value, msg);
            case "schema":
                throw new AssertionError("schema 算子在 Java 版暂未实现，可使用其它断言算子替代");
            default:
                throw new AssertionError("未知断言算子 '" + name + "'");
        }
    }
}
