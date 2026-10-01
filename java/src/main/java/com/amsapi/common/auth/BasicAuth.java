package com.amsapi.common.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP Basic：Authorization: Basic base64(user:password)。 */
public class BasicAuth extends AuthStrategy {
    private final String username;
    private final String password;

    public BasicAuth(String username, String password) {
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    @Override
    public String name() {
        return "basic";
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, String> cookies, Map<String, Object> params) {
        String user = resolveCredential(username);
        String pwd = resolveCredential(password);
        String raw = user + ":" + pwd;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        headers.put("Authorization", "Basic " + encoded);
    }

    @Override
    public Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("认证方式", name());
        info.put("用户名", username);
        return info;
    }
}
