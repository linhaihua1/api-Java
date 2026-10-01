package com.amsapi.common.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/** 自定义请求头携带令牌（如 access-ticket、X-Auth-Token）。 */
public class HeaderAuth extends AuthStrategy {
    private final String headerName;
    private final String value;
    private final String prefix;

    public HeaderAuth(String headerName, String value, String prefix) {
        this.headerName = (headerName == null || headerName.isEmpty()) ? "Token" : headerName;
        this.value = value == null ? "" : value;
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public String name() {
        return "header";
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, String> cookies, Map<String, Object> params) {
        headers.put(headerName, prefix + resolveCredential(value));
    }

    @Override
    public Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("认证方式", name());
        info.put("请求头", headerName);
        return info;
    }
}
