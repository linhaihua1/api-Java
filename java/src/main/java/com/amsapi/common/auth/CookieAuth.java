package com.amsapi.common.auth;

import com.amsapi.common.base.Base;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话 Cookie。
 * 支持两种写法：
 *   cookieString = "JSESSIONID=abc; token=xyz"
 *   cookies       = {"JSESSIONID": "abc"}
 */
public class CookieAuth extends AuthStrategy {
    private final Map<String, String> cookies = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public CookieAuth(Object cookiesObj, String cookieString) {
        if (cookiesObj instanceof Map) {
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) cookiesObj).entrySet()) {
                this.cookies.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        if (cookieString != null && !cookieString.isEmpty()) {
            for (String item : cookieString.split(";")) {
                if (item.contains("=")) {
                    int idx = item.indexOf("=");
                    this.cookies.put(item.substring(0, idx).trim(), item.substring(idx + 1).trim());
                }
            }
        }
    }

    @Override
    public String name() {
        return "cookie";
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, String> cookies, Map<String, Object> params) {
        for (Map.Entry<String, String> e : this.cookies.entrySet()) {
            cookies.put(e.getKey(), resolveCredential(e.getValue()));
        }
    }

    @Override
    public Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("认证方式", name());
        info.put("Cookie 数", String.valueOf(cookies.size()));
        return info;
    }
}
