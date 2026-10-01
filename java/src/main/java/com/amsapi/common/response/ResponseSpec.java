package com.amsapi.common.response;

import com.amsapi.common.Constants;
import com.amsapi.common.base.Base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 响应判定层。
 * 对应 Python 版 common/response.py。
 *
 * 不同 web 系统对「业务是否成功」的表达方式差异极大，本模块把这些差异收敛为
 * 一份可配置的判定规则，由系统适配档案的 [response] 段声明。
 *
 * 判定语义：
 *   用例给了 expected_code  -> 用 expected_code 与「实际业务码」比较
 *   用例没给 expected_code  -> 用 successValues 集合判断
 *   两者都没有              -> 只看 HTTP 状态码
 */
public class ResponseSpec {
    private static final Logger logger = LoggerFactory.getLogger(ResponseSpec.class);

    // 风格常量
    public static final String STYLE_STATUS = Constants.STYLE_STATUS;
    public static final String STYLE_CODE = Constants.STYLE_CODE;
    public static final String STYLE_SUCCESS = Constants.STYLE_SUCCESS;
    public static final String STYLE_ERRNO = Constants.STYLE_ERRNO;
    public static final String STYLE_RETCODE = Constants.STYLE_RETCODE;
    public static final String STYLE_RAW = Constants.STYLE_RAW;

    public static final List<String> STYLES = Arrays.asList(
            STYLE_STATUS, STYLE_CODE, STYLE_SUCCESS, STYLE_ERRNO, STYLE_RETCODE, STYLE_RAW);

    private static final Map<String, String[]> STYLE_PATHS = new LinkedHashMap<>();
    private static final Map<String, String[]> STYLE_SUCCESS_VALUES = new LinkedHashMap<>();

    static {
        STYLE_PATHS.put(STYLE_STATUS, new String[]{"body.status"});
        STYLE_PATHS.put(STYLE_CODE, new String[]{"body.code", "body.retCode", "body.resultCode"});
        STYLE_PATHS.put(STYLE_SUCCESS, new String[]{"body.success", "body.ok", "body.isSuccess"});
        STYLE_PATHS.put(STYLE_ERRNO, new String[]{"body.errno", "body.errCode", "body.errorCode"});
        STYLE_PATHS.put(STYLE_RETCODE, new String[]{"body.retCode", "body.retcode", "body.returnCode"});
        STYLE_PATHS.put(STYLE_RAW, new String[]{});

        STYLE_SUCCESS_VALUES.put(STYLE_STATUS, new String[]{});
        STYLE_SUCCESS_VALUES.put(STYLE_CODE, new String[]{});
        STYLE_SUCCESS_VALUES.put(STYLE_SUCCESS, new String[]{"true", "1", "yes", "ok"});
        STYLE_SUCCESS_VALUES.put(STYLE_ERRNO, new String[]{"0", "200"});
        STYLE_SUCCESS_VALUES.put(STYLE_RETCODE, new String[]{"0000", "0", "200", "success"});
        STYLE_SUCCESS_VALUES.put(STYLE_RAW, new String[]{});
    }

    private static final String[] DEFAULT_MESSAGE_PATHS = {
            "body.msg", "body.message", "body.errmsg", "body.errMsg",
            "body.retMsg", "body.retmsg", "body.info", "body.error", "body.errorMessage"
    };
    private static final String[] DEFAULT_DATA_PATHS = {
            "body.data", "body.result", "body.rows", "body.list",
            "body.content", "body.payload", "body.records"
    };

    private final String style;
    private final Set<Integer> httpOk;
    private final String[] codePaths;
    private final List<String> successValues;
    private final String[] messagePaths;
    private final String[] dataPaths;
    private boolean warned = false;

    public ResponseSpec(String style, String successPath, Object successValues,
                        String messagePath, String dataPath, Object httpOk) {
        this.style = (style != null && STYLES.contains(style)) ? style : STYLE_STATUS;
        this.httpOk = asHttpCodes(httpOk);

        if (successPath != null && !successPath.trim().isEmpty()) {
            this.codePaths = new String[]{successPath.trim()};
        } else {
            this.codePaths = STYLE_PATHS.getOrDefault(this.style, new String[]{});
        }

        Object values = successValues != null ? successValues : STYLE_SUCCESS_VALUES.get(this.style);
        this.successValues = asList(values);

        this.messagePaths = (messagePath != null && !messagePath.trim().isEmpty())
                ? new String[]{messagePath.trim()} : DEFAULT_MESSAGE_PATHS;
        this.dataPaths = (dataPath != null && !dataPath.trim().isEmpty())
                ? new String[]{dataPath.trim()} : DEFAULT_DATA_PATHS;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private static List<String> asList(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object item : (Collection<Object>) value) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) result.add(s);
            }
            return result;
        }
        if (value instanceof Object[]) {
            List<String> result = new ArrayList<>();
            for (Object item : (Object[]) value) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) result.add(s);
            }
            return result;
        }
        String text = String.valueOf(value).replace("，", ",").replace(";", ",");
        List<String> result = new ArrayList<>();
        for (String item : text.split(",")) {
            String s = item.trim();
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    private static Set<Integer> asHttpCodes(Object values) {
        Set<Integer> defaultCodes = new LinkedHashSet<>(Arrays.asList(200, 201, 202, 204));
        List<String> items = asList(values);
        if (items.isEmpty()) return defaultCodes;
        Set<Integer> codes = new LinkedHashSet<>();
        for (String item : items) {
            try {
                codes.add(Integer.parseInt(item));
            } catch (NumberFormatException ignored) {
            }
        }
        return codes.isEmpty() ? defaultCodes : codes;
    }

    private Object pick(Map<String, Object> response, String[] paths) {
        for (String path : paths) {
            Object value = Base.parseRelation(Arrays.asList(path.split("\\.")), response);
            if (value != null) return value;
        }
        return null;
    }

    public static boolean equal(Object actual, Object expected) {
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

    // ------------------------------------------------------------------
    // 对外能力
    // ------------------------------------------------------------------
    public boolean isHttpOk(Map<String, Object> response) {
        if (response == null) return false;
        Object code = response.get(Constants.RES_CODE);
        if (code == null) return false;
        try {
            return httpOk.contains(Integer.parseInt(String.valueOf(code).trim()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Object actualCode(Map<String, Object> response) {
        if (codePaths.length == 0) {
            return response == null ? null : response.get(Constants.RES_CODE);
        }
        return pick(response, codePaths);
    }

    public boolean isSuccess(Map<String, Object> response, Object expected) {
        if (response == null) return false;
        if (!isHttpOk(response)) return false;

        Object actual = actualCode(response);

        if (expected != null && !String.valueOf(expected).isEmpty()) {
            return equal(actual, expected);
        }

        if (!successValues.isEmpty()) {
            for (String v : successValues) {
                if (equal(actual, v)) return true;
            }
            return false;
        }

        if (!STYLE_RAW.equals(style) && !warned) {
            warned = true;
            logger.warn("响应判定配置不完整：风格 '{}' 既无 expected_code 也无 success_values，" +
                    "当前仅按 HTTP 状态码判成功。", style);
        }
        return true;
    }

    public String message(Map<String, Object> response) {
        Object value = pick(response, messagePaths);
        return value == null ? "" : String.valueOf(value);
    }

    public Object data(Map<String, Object> response) {
        return pick(response, dataPaths);
    }

    public Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("判定风格", style);
        info.put("业务码路径", codePaths.length > 0 ? String.join(" | ", codePaths) : "(用 HTTP 状态码)");
        info.put("成功值集合", successValues.isEmpty() ? "(由用例 expected_code 提供)" : String.join(",", successValues));
        return info;
    }

    public String getStyle() {
        return style;
    }
}
