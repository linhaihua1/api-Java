package com.amsapi.common.auth;

import com.amsapi.common.Constants;
import com.amsapi.config.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OAuth2 client_credentials 模式。
 * 首次请求前自动调用 token_url 取令牌，按 expires_in 缓存；
 * 到期前若干秒自动续期（提前量可配）。
 */
public class OAuth2ClientCredentials extends AuthStrategy {
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String header;
    private final String prefix;
    private final String authMode;
    private final int timeout;
    private final boolean verify;
    private final int refreshAhead;

    private volatile String token;
    private volatile long expireAt = 0L;
    private final ReentrantLock lock = new ReentrantLock();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public OAuth2ClientCredentials(String tokenUrl, String clientId, String clientSecret,
                                   String scope, String header, String prefix, String authMode,
                                   Integer timeout, Boolean verify, Integer refreshAhead) {
        this.tokenUrl = tokenUrl == null ? "" : tokenUrl;
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.scope = scope == null ? "" : scope;
        this.header = (header == null || header.isEmpty()) ? "Authorization" : header;
        this.prefix = prefix == null ? "Bearer " : prefix;
        this.authMode = (authMode == null || authMode.isEmpty()) ? "post" : authMode.trim().toLowerCase();
        this.timeout = timeout != null ? timeout : Settings.AuthConfig.TOKEN_TIMEOUT;
        this.verify = verify != null ? verify : Settings.AuthConfig.TOKEN_VERIFY;
        this.refreshAhead = refreshAhead != null ? refreshAhead : Settings.AuthConfig.REFRESH_AHEAD;
    }

    @Override
    public String name() {
        return "oauth2";
    }

    @SuppressWarnings("unchecked")
    private String fetchToken() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("grant_type", "client_credentials");
        if (!scope.isEmpty()) payload.put("scope", scope);

        HttpPost post = new HttpPost(tokenUrl);
        post.setHeader(Constants.HDR_CONTENT_TYPE, Constants.CT_JSON);

        if ("basic".equals(authMode)) {
            String raw = clientId + ":" + clientSecret;
            String encoded = java.util.Base64.getEncoder()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            post.setHeader("Authorization", "Basic " + encoded);
        } else {
            payload.put("client_id", clientId);
            payload.put("client_secret", clientSecret);
        }

        post.setEntity(new StringEntity(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8));

        logger.info("【认证】OAuth2 申请令牌：{}", tokenUrl);

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (status != 200) {
                throw new RuntimeException("OAuth2 取令牌失败：HTTP " + status + " " + body.substring(0, Math.min(200, body.length())));
            }
            Map<String, Object> data = MAPPER.readValue(body, Map.class);
            String tok = (String) data.get("access_token");
            if (tok == null) tok = (String) data.get("token");
            if (tok == null || tok.isEmpty()) {
                throw new RuntimeException("OAuth2 响应中未找到 access_token：" + data);
            }
            Object expiresIn = data.get("expires_in");
            if (expiresIn == null) expiresIn = data.get("expiresIn");
            double exp = 3600;
            if (expiresIn != null) exp = Double.parseDouble(String.valueOf(expiresIn));

            this.token = tok;
            this.expireAt = (long) (System.currentTimeMillis() / 1000.0 + exp);
            logger.info("【认证】OAuth2 令牌获取成功，有效期 {} 秒", exp);
            return tok;
        }
    }

    public String getToken() {
        lock.lock();
        try {
            if (token == null || System.currentTimeMillis() / 1000.0 >= expireAt - refreshAhead) {
                try {
                    return fetchToken();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return token;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, String> cookies, Map<String, Object> params) {
        headers.put(header, prefix + getToken());
    }

    @Override
    public Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("认证方式", name());
        info.put("取令牌地址", tokenUrl);
        info.put("模式", authMode);
        return info;
    }
}
