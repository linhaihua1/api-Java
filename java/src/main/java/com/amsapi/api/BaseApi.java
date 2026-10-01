package com.amsapi.api;

import com.amsapi.common.Constants;
import com.amsapi.common.asserts.Asserts;
import com.amsapi.common.base.Base;
import com.amsapi.common.auth.AuthStrategy;
import com.amsapi.common.exceptions.CaseSkipped;
import com.amsapi.common.hooks.Hooks;
import com.amsapi.common.profile.SystemProfile;
import com.amsapi.common.response.ResponseSpec;
import com.amsapi.common.variable.GlobalVar;
import com.amsapi.config.Settings;
import com.amsapi.utils.AllureUtil;
import com.amsapi.utils.LogUtil;
import com.amsapi.utils.RequestSend;
import org.slf4j.Logger;

import java.util.*;

/**
 * 接口层基类（API Object 基类）。
 * 对应 Python 版 api/base_api.py。
 *
 * BaseApi 提供 8 项统一能力：
 *   1. URL 组装      —— 相对路径拼主域名、支持域名别名
 *   2. 参数化        —— 请求前把 ${var} 替换为全局变量池中的真实值
 *   3. 认证          —— 按系统适配档案自动附加令牌
 *   4. 协议钩子      —— 请求前签名、时间戳；响应后解密
 *   5. 统一请求      —— 复用 RequestSend
 *   6. 关联提取      —— 把响应中的关键字段写入全局变量池
 *   7. 统一断言      —— 业务成功判定 + HTTP 状态码 + 21 种字段级断言
 *   8. 用例生命周期  —— setup / teardown / depends / 分页抓取
 */
public class BaseApi {
    protected static final Logger logger = LogUtil.logger;
    private static final String CASE_STATUS_PREFIX = Constants.CASE_STATUS_PREFIX;

    protected SystemProfile profile;
    protected AuthStrategy auth;
    protected String baseUrl;
    protected Map<String, String> commonHeaders;
    protected Map<String, String> commonCookies;
    protected int timeout;
    protected RequestSend client;
    protected volatile Map<String, Object> res;
    protected Map<String, Object> currentCase;

    public BaseApi() {
        this(null, null, null, null, null, null, null);
    }

    public BaseApi(String baseUrl, Map<String, String> headers, Integer timeout,
                   Object session, SystemProfile profile, AuthStrategy auth,
                   Map<String, String> cookies) {
        this.profile = profile != null ? profile : SystemProfile.loadProfile();
        this.auth = auth != null ? auth : this.profile.auth;

        String bu = baseUrl != null ? baseUrl : this.profile.getEffectiveBaseUrl();
        this.baseUrl = bu != null ? (bu.endsWith("/") ? bu.substring(0, bu.length() - 1) : bu) : "";

        this.commonHeaders = new LinkedHashMap<>(this.profile.headers);
        if (headers != null) this.commonHeaders.putAll(headers);

        this.commonCookies = new LinkedHashMap<>(this.profile.cookies);
        if (cookies != null) this.commonCookies.putAll(cookies);

        this.timeout = timeout != null ? timeout : this.profile.getTimeout();
        this.client = new RequestSend(this.timeout, this.profile.getVerify(),
                null, this.profile.getEncoding(), this.profile.getResponseFormat());
        this.res = null;
        this.currentCase = null;
    }

    // ==================================================================
    // 一、URL 组装
    // ==================================================================
    private String fullUrl(Object path, String host) {
        if (path == null) {
            throw new IllegalArgumentException("请求路径不能为空");
        }
        String p = String.valueOf(path);
        if (p.isEmpty()) {
            throw new IllegalArgumentException("请求路径不能为空");
        }
        if (p.toLowerCase().startsWith("http://") || p.toLowerCase().startsWith("https://")) {
            return p;
        }
        return profile.resolveUrl(p, host, baseUrl);
    }

    // ==================================================================
    // 二、参数化
    // ==================================================================
    public static Object resolve(Object data) {
        if (data == null) return null;
        if (!Base.find(data).isEmpty()) {
            return Base.replaceObj(data);
        }
        return data;
    }

    // ==================================================================
    // 三、认证与钩子
    // ==================================================================
    @SuppressWarnings("unchecked")
    private Object[] applyAuth(String method, Map<String, String> headers,
                               Map<String, String> cookies, Object payload) {
        Map<String, Object> authParams = new LinkedHashMap<>();
        auth.apply(headers, cookies, authParams);
        if (authParams.isEmpty()) {
            return new Object[]{headers, cookies, payload, null};
        }
        String location = RequestSend.bodyLocation(method, headers);
        if ("params".equals(location)) {
            Map<String, Object> merged = new LinkedHashMap<>(authParams);
            if (payload instanceof Map) {
                merged.putAll((Map<String, Object>) payload);
                return new Object[]{headers, cookies, merged, null};
            } else if (payload == null) {
                return new Object[]{headers, cookies, merged, null};
            }
            return new Object[]{headers, cookies, payload, authParams};
        }
        return new Object[]{headers, cookies, payload, authParams};
    }

    @SuppressWarnings("unchecked")
    private Object[] applyBeforeHooks(String method, String url, Map<String, String> headers,
                                      Map<String, String> cookies, Object payload) {
        if (!profile.hooks.hasBefore()) {
            return new Object[]{headers, cookies, payload};
        }
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("method", method.toUpperCase());
        ctx.put("url", url);
        ctx.put("headers", headers);
        ctx.put("cookies", cookies);
        ctx.put("payload", payload);
        ctx.put("location", RequestSend.bodyLocation(method, headers));
        ctx.put("case", currentCase);
        ctx.put("profile", profile);
        ctx = profile.hooks.runBefore(ctx);
        return new Object[]{
                (Map<String, String>) ctx.getOrDefault("headers", headers),
                (Map<String, String>) ctx.getOrDefault("cookies", cookies),
                ctx.getOrDefault("payload", payload)
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyAfterHooks(Map<String, Object> response) {
        if (!profile.hooks.hasAfter()) return response;
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("response", response);
        ctx.put("case", currentCase);
        ctx.put("profile", profile);
        ctx = profile.hooks.runAfter(ctx);
        return (Map<String, Object>) ctx.getOrDefault("response", response);
    }

    // ==================================================================
    // 四、统一请求
    // ==================================================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> request(String method, Object path, Object data,
                                       Map<String, String> headers, Map<String, String> cookies,
                                       String host, Map<String, Object> extraParams) throws Exception {
        // URL 参数化
        String url = fullUrl(resolve(path), host);

        // 合并请求头
        Map<String, String> mergedHeaders = new LinkedHashMap<>(commonHeaders);
        if (headers != null) mergedHeaders.putAll(headers);
        mergedHeaders = (Map<String, String>) resolve(mergedHeaders);

        // payload 参数化
        Object payload = resolve(Base.safeLoads(data, data));

        // Cookie
        Map<String, String> cookieJar = new LinkedHashMap<>(commonCookies);
        if (cookies != null) cookieJar.putAll(cookies);
        cookieJar = (Map<String, String>) resolve(cookieJar);

        // 认证
        Object[] authResult = applyAuth(method, mergedHeaders, cookieJar, payload);
        mergedHeaders = (Map<String, String>) authResult[0];
        cookieJar = (Map<String, String>) authResult[1];
        payload = authResult[2];
        Map<String, Object> extraParams2 = (Map<String, Object>) authResult[3];

        // 钩子
        Object[] hookResult = applyBeforeHooks(method, url, mergedHeaders, cookieJar, payload);
        mergedHeaders = (Map<String, String>) hookResult[0];
        cookieJar = (Map<String, String>) hookResult[1];
        payload = hookResult[2];

        logger.info("【请求】{} {}", method.toUpperCase(), url);

        // Allure 附件
        String payloadName = RequestSend.bodyLocation(method, mergedHeaders);
        if ("params".equals(payloadName)) {
            AllureUtil.attachRequest(method, url, mergedHeaders, null, payload);
        } else {
            AllureUtil.attachRequest(method, url, mergedHeaders, payload, null);
        }

        Map<String, Object> sendParams = extraParams2 != null ? extraParams2 : extraParams;
        res = client.send(url, method, payload, mergedHeaders, cookieJar, sendParams, null);
        res = applyAfterHooks(res);
        AllureUtil.attachResponse(res);
        return res;
    }

    // ==================================================================
    // 五、关联提取
    // ==================================================================
    public Map<String, Object> extract(String relation) {
        if (relation == null || relation.trim().isEmpty()
                || "none".equalsIgnoreCase(relation.trim()) || "null".equalsIgnoreCase(relation.trim())) {
            return Collections.emptyMap();
        }
        Object root = res;
        Map<String, Object> extracted = new LinkedHashMap<>();
        for (String expr : relation.split(",")) {
            expr = expr.trim();
            if (expr.isEmpty()) continue;
            Object[] parsed = Base.parseExpression(expr, root);
            String name = (String) parsed[0];
            Object value = parsed[1];
            GlobalVar.set(name, value);
            extracted.put(name, value);
            logger.info("【关联】{} = {}", name, value);
        }
        return extracted;
    }

    // ==================================================================
    // 六、统一断言
    // ==================================================================
    public boolean checkStatus(int expectedCode) {
        Object actual = res != null ? res.get(Constants.RES_CODE) : null;
        int actualInt;
        try {
            actualInt = actual == null ? -1 : Integer.parseInt(String.valueOf(actual).trim());
        } catch (NumberFormatException e) {
            actualInt = -1;
        }
        if (actualInt != expectedCode) {
            throw new AssertionError("HTTP 状态码断言失败：期望 " + expectedCode + "，实际 " + actual);
        }
        return true;
    }

    public boolean checkSuccess(Object expected, String msg) {
        ResponseSpec spec = profile.response;
        if (spec.isSuccess(res, expected)) return true;
        Object actual = spec.actualCode(res);
        String message = spec.message(res);
        throw new AssertionError(
                (msg != null ? msg : "") + "业务断言失败：期望 " +
                        (expected != null && !String.valueOf(expected).isEmpty() ? expected
                                : "成功") + "，实际 " + actual +
                        "（风格=" + spec.getStyle() + "）\n" +
                        "  服务端消息：" + (message != null && !message.isEmpty() ? message : "(无)") + "\n" +
                        "  响应摘要：" + Asserts.summary(res));
    }

    public boolean checkJson(String jsonPath, Object expected) {
        Object actual = Base.parseRelation(Arrays.asList(jsonPath.split("\\.")), res);
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("字段断言失败：" + jsonPath + " 期望 " + expected + "，实际 " + actual);
        }
        return true;
    }

    // 字段级断言
    public boolean assertEquals(String path, Object expected, String msg) {
        return Asserts.equals(res, path, expected, msg);
    }
    public boolean assertContains(String path, Object expected, String msg) {
        return Asserts.contains(res, path, expected, msg);
    }
    public boolean assertGt(String path, Object expected, String msg) {
        return Asserts.gt(res, path, expected, msg);
    }
    public boolean assertNotEmpty(String path, String msg) {
        return Asserts.notEmpty(res, path, msg);
    }
    public boolean assertType(String path, Object expected, String msg) {
        return Asserts.typeIs(res, path, expected, msg);
    }

    public boolean checkExpects(Object expects) {
        List<Map<String, Object>> items = asExpects(expects);
        if (items.isEmpty()) return true;
        for (Map<String, Object> item : items) {
            String op = String.valueOf(item.get("op"));
            String path = item.get("path") != null ? String.valueOf(item.get("path")) : null;
            Object value = item.get("value");
            String msg = item.get("msg") != null ? String.valueOf(item.get("msg")) : "";
            logger.info("【断言】{} {} {}", op, path != null ? path : "", value != null ? value : "");
            Asserts.runOp(op, res, path, value, msg);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asExpects(Object expects) {
        if (expects == null || "".equals(expects) || (expects instanceof List && ((List<?>) expects).isEmpty())) {
            return Collections.emptyList();
        }
        if (expects instanceof String) {
            String text = ((String) expects).trim();
            if (text.isEmpty()) return Collections.emptyList();
            // 尝试 JSON 解析
            if (text.startsWith("[") || text.startsWith("{")) {
                Object parsed = Base.safeLoads(text, null);
                if (parsed != null) return asExpects(parsed);
            }
            // 简写语法：op:path=value; op:path=value
            List<Map<String, Object>> results = new ArrayList<>();
            for (String chunk : text.split(";")) {
                chunk = chunk.trim();
                if (chunk.isEmpty()) continue;
                int eqIdx = chunk.indexOf("=");
                String left = eqIdx >= 0 ? chunk.substring(0, eqIdx) : chunk;
                String value = eqIdx >= 0 ? chunk.substring(eqIdx + 1) : null;
                int colonIdx = left.indexOf(":");
                String op = colonIdx >= 0 ? left.substring(0, colonIdx).trim() : left.trim();
                String path = colonIdx >= 0 ? left.substring(colonIdx + 1).trim() : null;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("op", op);
                item.put("path", (path != null && !path.isEmpty()) ? path : null);
                item.put("value", value);
                results.add(item);
            }
            return results;
        }
        if (expects instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) expects;
            if (m.isEmpty()) return Collections.emptyList();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("op", m.getOrDefault("op", m.get("断言")));
            item.put("path", m.getOrDefault("path", m.get("路径")));
            item.put("value", m.containsKey("value") ? m.get("value") : m.get("值"));
            item.put("msg", m.getOrDefault("msg", ""));
            return Collections.singletonList(item);
        }
        if (expects instanceof List) {
            List<Map<String, Object>> results = new ArrayList<>();
            for (Object item : (List<Object>) expects) {
                results.addAll(asExpects(item));
            }
            return results;
        }
        return Collections.emptyList();
    }

    // ==================================================================
    // 七、用例生命周期
    // ==================================================================
    public void checkDepends(Map<String, Object> caseData) {
        Object raw = caseData != null ? caseData.get(Constants.FIELD_DEPENDS) : null;
        if (raw == null || String.valueOf(raw).isEmpty()) return;
        String text = String.valueOf(raw).replace("，", ",");
        for (String caseId : text.split(",")) {
            caseId = caseId.trim();
            if (caseId.isEmpty()) continue;
            Object status = GlobalVar.get(CASE_STATUS_PREFIX + caseId, null);
            if (status == null) {
                throw new CaseSkipped("依赖的用例 id=" + caseId + " 尚未执行，无法确认前置条件");
            }
            if (!Constants.STATUS_PASS.equals(status)) {
                throw new CaseSkipped("依赖的用例 id=" + caseId + " 执行未通过，本用例跳过");
            }
        }
    }

    public static void recordCaseStatus(Map<String, Object> caseData, boolean isPass) {
        Object caseId = caseData != null ? caseData.get(Constants.FIELD_ID) : null;
        if (caseId == null || String.valueOf(caseId).isEmpty()) return;
        GlobalVar.set(CASE_STATUS_PREFIX + String.valueOf(caseId), isPass ? Constants.STATUS_PASS : Constants.STATUS_FAIL);
    }

    @SuppressWarnings("unchecked")
    private List<Object> asSteps(Object steps) {
        if (steps == null || "".equals(steps) || (steps instanceof List && ((List<?>) steps).isEmpty())
                || (steps instanceof Map && ((Map<?, ?>) steps).isEmpty())) {
            return Collections.emptyList();
        }
        if (steps instanceof String) {
            Object parsed = Base.safeLoads(((String) steps).trim(), null);
            if (parsed != null && !parsed.equals(steps)) return asSteps(parsed);
            return Collections.singletonList(steps);
        }
        if (steps instanceof Map) return Collections.singletonList(steps);
        if (steps instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<Object>) steps) {
                if (item != null) result.add(item);
            }
            return result;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runSteps(Object steps, String label) throws Exception {
        List<Map<String, Object>> responses = new ArrayList<>();
        List<Object> stepList = asSteps(steps);
        for (int i = 0; i < stepList.size(); i++) {
            Object step = stepList.get(i);
            Map<String, Object> stepMap;
            if (step instanceof String) {
                String[] parts = ((String) step).split("\\s+", 2);
                if (parts.length < 2) {
                    logger.warn("{}第 {} 步格式无法识别，已跳过：{}", label, i + 1, step);
                    continue;
                }
                stepMap = new LinkedHashMap<>();
                stepMap.put(Constants.FIELD_METHOD, parts[0]);
                stepMap.put(Constants.FIELD_URL, parts[1]);
            } else if (step instanceof Map) {
                stepMap = (Map<String, Object>) step;
            } else {
                continue;
            }
            logger.info("【{}】第 {} 步：{} {}", label, i + 1, stepMap.get(Constants.FIELD_METHOD), stepMap.get(Constants.FIELD_URL));
            doRequest(stepMap);
            extract((String) stepMap.getOrDefault(Constants.FIELD_RELATION, stepMap.get(Constants.FIELD_EXTRACT)));
            responses.add(res);
        }
        return responses;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doRequest(Map<String, Object> caseData) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        Object h = Base.safeLoads(caseData.get(Constants.FIELD_HEADERS), new LinkedHashMap<>());
        if (h instanceof Map) headers.putAll((Map<String, String>) h);

        Object ct = caseData.get(Constants.FIELD_CONTENT_TYPE);
        if (ct == null) ct = caseData.get(Constants.FIELD_REQUEST_TYPE);
        if (ct == null) ct = profile.getContentType();
        if (ct != null && !hasHeader(headers, "Content-Type")) {
            headers.put("Content-Type", String.valueOf(ct));
        }

        Object methodObj = caseData.get(Constants.FIELD_METHOD);
        if (methodObj == null || String.valueOf(methodObj).trim().isEmpty()) {
            throw new IllegalArgumentException("用例缺少必填字段 method：" + caseData.get(Constants.FIELD_ID));
        }
        res = request(
                String.valueOf(methodObj),
                caseData.get(Constants.FIELD_URL),
                Base.safeLoads(caseData.get(Constants.FIELD_REQUEST_BODY), null),
                headers,
                (Map<String, String>) Base.safeLoads(caseData.get(Constants.FIELD_COOKIES), new LinkedHashMap<>()),
                caseData.get(Constants.FIELD_HOST) != null ? String.valueOf(caseData.get(Constants.FIELD_HOST)) : null,
                null
        );
        return res;
    }

    private boolean hasHeader(Map<String, String> headers, String name) {
        String target = name.toLowerCase();
        for (String key : headers.keySet()) {
            if (key.toLowerCase().equals(target)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> runCase(Map<String, Object> caseData) throws Exception {
        if (caseData == null) caseData = new LinkedHashMap<>();
        currentCase = caseData;
        res = null;

        checkDepends(caseData);
        runSteps(caseData.get(Constants.FIELD_SETUP), "前置");

        boolean isPass = false;
        try {
            doRequest(caseData);
            Object rel = caseData.get(Constants.FIELD_RELATION);
            if (rel == null) rel = caseData.get(Constants.FIELD_EXTRACT);
            extract(rel != null ? String.valueOf(rel) : null);
            checkExpects(caseData.get(Constants.FIELD_EXPECTS));
            if (Base.toBool(caseData.get(Constants.FIELD_CHECK_BUSINESS), true)) {
                checkSuccess(caseData.get(Constants.FIELD_EXPECTED_CODE), "");
            }
            isPass = true;
            return res;
        } finally {
            recordCaseStatus(caseData, isPass);
            try {
                runSteps(caseData.get(Constants.FIELD_TEARDOWN), "后置清理");
            } catch (Exception exc) {
                logger.error("后置清理步骤执行失败（不影响用例结论）：{}", exc.getMessage());
            } finally {
                currentCase = null;
            }
        }
    }

    // ==================================================================
    // 便捷属性
    // ==================================================================
    public Object getBody() { return res != null ? res.get(Constants.RES_BODY) : null; }
    public Object getCode() { return res != null ? res.get(Constants.RES_CODE) : null; }
    public Object getBusinessCode() { return profile.response.actualCode(res); }
    public String getMessage() { return profile.response.message(res); }
    public Object getElapsed() { return res != null ? res.get(Constants.RES_ELAPSED) : null; }
    public Map<String, Object> getResponse() { return res; }
}
