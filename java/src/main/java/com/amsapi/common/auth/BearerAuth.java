package com.amsapi.common.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bearer Token（含 JWT）：Authorization: Bearer <token>。 */
public class BearerAuth extends AuthStrategy {
    private final String token;
    private final String header;
    private final String prefix;

    public BearerAuth(String token, String header, String prefix) {
        this.token = token == null ? "" : token;
        this.header = (header == null || header.isEmpty()) ? "Authorization" : header;
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public String name() {
        return "bearer";
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, String> cookies, Map<String, Object> params) {
        headers.put(header, prefix + resolveCredential(token));
    }

    @Override
    public Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("认证方式", name());
        info.put("请求头", header);
        return info;
    }
}
