package com.amsapi.common.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/** API Key：可放请求头（默认）或 query 参数。 */
public class ApiKeyAuth extends AuthStrategy {
    private final String keyName;
    private final String value;
    private final String location;

    public ApiKeyAuth(String keyName, String value, String location) {
        this.keyName = (keyName == null || keyName.isEmpty()) ? "X-Api-Key" : keyName;
        this.value = value == null ? "" : value;
        this.location = (location == null || location.isEmpty()) ? "header" : location.trim().toLowerCase();
    }

    @Override
    public String name() {
        return "apikey";
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, String> cookies, Map<String, Object> params) {
        String v = resolveCredential(value);
        if ("query".equals(location)) {
            params.put(keyName, v);
        } else {
            headers.put(keyName, v);
        }
    }

    @Override
    public Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("认证方式", name());
        info.put("参数名", keyName);
        info.put("位置", location);
        return info;
    }
}
