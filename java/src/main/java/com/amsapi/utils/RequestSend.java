package com.amsapi.utils;

import com.amsapi.common.Constants;
import com.amsapi.common.base.Base;
import com.amsapi.config.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP 请求工具层。
 * 对应 Python 版 utils/requestsutil.py。
 *
 * 在 Apache HttpClient 之上做一层统一封装：
 *   1. 支持 get/post/put/delete/patch/head/options
 *   2. 自动根据 method 与 Content-Type 决定参数挂载位置
 *   3. 统一超时、证书校验、会话复用
 *   4. 统一返回结构，附带耗时
 *
 * 返回结构：
 *   {
 *     "code":    200,            # HTTP 状态码
 *     "body":    {...}|"文本",    # 响应体
 *     "cookies": {...},          # 响应 Cookie
 *     "headers": {...},          # 响应头
 *     "elapsed": 123.45,         # 耗时（毫秒）
 *     "url":     "最终地址",
 *     "method":  "GET"
 *   }
 */
public class RequestSend {
    private static final Logger logger = LoggerFactory.getLogger(RequestSend.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int timeout;
    private final boolean verify;
    private final CloseableHttpClient session;
    private final String encoding;
    private final String responseFormat;

    public RequestSend() {
        this(Settings.RunConfig.TIMEOUT, Settings.RunConfig.VERIFY, null, null, null);
    }

    public RequestSend(Integer timeout, Boolean verify, CloseableHttpClient session,
                       String encoding, String responseFormat) {
        this.timeout = timeout != null ? timeout : Settings.RunConfig.TIMEOUT;
        this.verify = verify != null ? verify : Settings.RunConfig.VERIFY;
        if (session != null) {
            this.session = session;
        } else {
            // 应用超时配置到 HttpClient
            int timeoutMs = this.timeout * Constants.MS_PER_SECOND;
            RequestConfig rc = RequestConfig.custom()
                    .setConnectTimeout(timeoutMs)
                    .setConnectionRequestTimeout(timeoutMs)
                    .setSocketTimeout(timeoutMs)
                    .build();
            this.session = HttpClients.custom().setDefaultRequestConfig(rc).build();
        }
        this.encoding = encoding;
        this.responseFormat = (responseFormat != null ? responseFormat : Constants.DEFAULT_RESPONSE_FORMAT).toLowerCase();
    }

    // ------------------------------------------------------------------
    // Content-Type 判定
    // ------------------------------------------------------------------
    public static String contentType(Map<String, String> headers) {
        if (headers == null) return "";
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase("content-type")) {
                return e.getValue().toLowerCase();
            }
        }
        return "";
    }

    private static boolean isRawText(Map<String, String> headers) {
        String ct = contentType(headers);
        return ct.startsWith("application/xml") || ct.startsWith("text/xml")
                || ct.startsWith("application/soap") || ct.startsWith("text/plain");
    }

    /**
     * 判定请求数据应挂载到哪个位置。
     * @return "params" | "json" | "data" | "files"
     */
    public static String bodyLocation(String method, Map<String, String> headers) {
        method = method.toLowerCase();
        if (method.equals("get") || method.equals("head") || method.equals("options")) {
            return "params";
        }
        String ct = contentType(headers);
        if (method.equals("delete") && ct.isEmpty()) return "params";
        if (ct.startsWith(Constants.CT_MULTIPART)) return Constants.LOC_FILES;
        if (ct.startsWith(Constants.CT_FORM)) return Constants.LOC_DATA;
        if (isRawText(headers)) return "data";
        return "json";
    }

    // ------------------------------------------------------------------
    // 响应解析
    // ------------------------------------------------------------------
    private String decodeText(HttpResponse res) {
        String text;
        try {
            if (encoding != null && !encoding.isEmpty()) {
                text = EntityUtils.toString(res.getEntity(), Charset.forName(encoding));
            } else {
                text = EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private Object parseBody(HttpResponse res, String format) {
        String fmt = (format != null ? format : responseFormat).toLowerCase();
        String text = decodeText(res);

        // 先尝试 JSON（除 text 外）
        if (!"text".equals(fmt)) {
            try {
                return MAPPER.readValue(text, Object.class);
            } catch (Exception ignored) {
            }
        }

        if ("xml".equals(fmt)) {
            Map<String, Object> parsed = Base.xmlToDict(text);
            if (parsed != null) return parsed;
        }
        return text;
    }

    // ------------------------------------------------------------------
    // 发送请求
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    public Map<String, Object> send(String url, String method, Object data,
                                    Map<String, String> headers, Map<String, String> cookies,
                                    Map<String, Object> params, Object files) throws Exception {
        method = method.toLowerCase();
        String location = bodyLocation(method, headers);

        // 构建请求
        String fullUrl = url;
        HttpRequestBase request;

        switch (method) {
            case "get": request = new HttpGet(fullUrl); break;
            case "post": request = new HttpPost(fullUrl); break;
            case "put": request = new HttpPut(fullUrl); break;
            case "delete": request = new HttpDelete(fullUrl); break;
            case "patch": request = new HttpPatch(fullUrl); break;
            case "head": request = new HttpHead(fullUrl); break;
            case "options": request = new HttpOptions(fullUrl); break;
            default: throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
        }

        // Headers
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.setHeader(e.getKey(), e.getValue());
            }
        }

        // 处理数据
        if (data != null) {
            if ("params".equals(location)) {
                // GET 等：数据作为 query 参数
                if (data instanceof Map) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, Object> e : ((Map<String, Object>) data).entrySet()) {
                        if (sb.length() > 0) sb.append("&");
                        sb.append(java.net.URLEncoder.encode(e.getKey(), "UTF-8"))
                                .append("=")
                                .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()), "UTF-8"));
                    }
                    if (sb.length() > 0) {
                        String sep = fullUrl.contains("?") ? "&" : "?";
                        request = cloneWithUrl(request, fullUrl + sep + sb.toString());
                    }
                }
            } else if ("json".equals(location)) {
                String jsonStr;
                if (data instanceof String) {
                    jsonStr = (String) data;
                } else {
                    jsonStr = MAPPER.writeValueAsString(data);
                }
                ((HttpEntityEnclosingRequestBase) request).setEntity(new StringEntity(jsonStr, StandardCharsets.UTF_8));
            } else if ("data".equals(location)) {
                // form 或 xml/text
                if (isRawText(headers)) {
                    String rawStr;
                    if (data instanceof String) rawStr = (String) data;
                    else if (data instanceof Map) rawStr = Base.dictToXml(data, "root", false, "utf-8");
                    else rawStr = MAPPER.writeValueAsString(data);
                    ((HttpEntityEnclosingRequestBase) request).setEntity(new StringEntity(rawStr, StandardCharsets.UTF_8));
                } else {
                    // form
                    if (data instanceof Map) {
                        StringBuilder sb = new StringBuilder();
                        for (Map.Entry<String, Object> e : ((Map<String, Object>) data).entrySet()) {
                            if (sb.length() > 0) sb.append("&");
                            sb.append(java.net.URLEncoder.encode(e.getKey(), "UTF-8"))
                                    .append("=")
                                    .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()), "UTF-8"));
                        }
                        ((HttpEntityEnclosingRequestBase) request).setEntity(new StringEntity(sb.toString(), StandardCharsets.UTF_8));
                    }
                }
            }
        }

        // 显式 params
        if (params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String currentUrl = request.getURI().toString();
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(java.net.URLEncoder.encode(e.getKey(), "UTF-8"))
                        .append("=")
                        .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()), "UTF-8"));
            }
            if (sb.length() > 0) {
                String sep = currentUrl.contains("?") ? "&" : "?";
                request = cloneWithUrl(request, currentUrl + sep + sb.toString());
            }
        }

        logger.info("==> {} {}", method.toUpperCase(), request.getURI());
        if (headers != null) logger.debug("headers = {}", headers);

        long start = System.currentTimeMillis();
        HttpResponse response;
        try {
            response = session.execute(request);
        } catch (Exception e) {
            logger.error("请求异常：{} {} -> {}", method.toUpperCase(), url, e.getMessage());
            throw e;
        }
        double elapsed = (System.currentTimeMillis() - start);

        int statusCode = response.getStatusLine().getStatusCode();
        Object body = parseBody(response, responseFormat);

        // 响应 cookies
        Map<String, String> respCookies = new LinkedHashMap<>();
        Header[] cookieHeaders = response.getHeaders("Set-Cookie");
        for (Header h : cookieHeaders) {
            String val = h.getValue();
            int eq = val.indexOf("=");
            if (eq > 0) respCookies.put(val.substring(0, eq).trim(), val.substring(eq + 1).split(";")[0].trim());
        }

        // 响应 headers
        Map<String, String> respHeaders = new LinkedHashMap<>();
        for (Header h : response.getAllHeaders()) {
            respHeaders.put(h.getName(), h.getValue());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", statusCode);
        result.put("body", body);
        result.put("cookies", respCookies);
        result.put("headers", respHeaders);
        result.put("elapsed", elapsed);
        result.put("url", request.getURI().toString());
        result.put("method", method.toUpperCase());

        logger.info("<== {} {} | {} ms", statusCode, response.getStatusLine().getReasonPhrase(), elapsed);
        logger.debug("响应体 = {}", body);

        return result;
    }

    private HttpRequestBase cloneWithUrl(HttpRequestBase request, String newUrl) throws Exception {
        String method = request.getMethod();
        HttpRequestBase newReq;
        switch (method.toLowerCase()) {
            case "get": newReq = new HttpGet(newUrl); break;
            case "post": newReq = new HttpPost(newUrl); break;
            case "put": newReq = new HttpPut(newUrl); break;
            case "delete": newReq = new HttpDelete(newUrl); break;
            case "patch": newReq = new HttpPatch(newUrl); break;
            case "head": newReq = new HttpHead(newUrl); break;
            case "options": newReq = new HttpOptions(newUrl); break;
            default: throw new IllegalArgumentException(method);
        }
        for (Header h : request.getAllHeaders()) {
            newReq.setHeader(h);
        }
        if (request instanceof HttpEntityEnclosingRequestBase) {
            org.apache.http.HttpEntity entity = ((HttpEntityEnclosingRequestBase) request).getEntity();
            if (entity != null) ((HttpEntityEnclosingRequestBase) newReq).setEntity(entity);
        }
        return newReq;
    }

    public CloseableHttpClient getSession() {
        return session;
    }
}
