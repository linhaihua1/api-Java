package com.amsapi.common.auth;

import com.amsapi.common.base.Base;
import com.amsapi.config.Settings;

import java.util.Map;

/**
 * 认证策略工厂。
 * 对应 Python 版 common/auth.py 的 build_auth()。
 */
public final class AuthFactory {

    public static final String AUTH_NONE = "none";
    public static final String AUTH_BASIC = "basic";
    public static final String AUTH_BEARER = "bearer";
    public static final String AUTH_HEADER = "header";
    public static final String AUTH_APIKEY = "apikey";
    public static final String AUTH_COOKIE = "cookie";
    public static final String AUTH_OAUTH2 = "oauth2";

    private AuthFactory() {
    }

    /**
     * 按适配档案 [auth] 段的配置构造认证策略。
     * @param cfg 小写键的配置字典
     */
    public static AuthStrategy build(Map<String, String> cfg) {
        if (cfg == null) cfg = new java.util.HashMap<>();
        String kind = (cfg.getOrDefault("type", AUTH_NONE)).trim().toLowerCase();

        switch (kind) {
            case "":
            case AUTH_NONE:
            case "no":
            case "false":
                return new NoAuth();
            case AUTH_BASIC:
                return new BasicAuth(cfg.getOrDefault("username", ""), cfg.getOrDefault("password", ""));
            case AUTH_BEARER:
                return new BearerAuth(cfg.getOrDefault("token", ""),
                        cfg.getOrDefault("header", "Authorization"),
                        cfg.getOrDefault("prefix", "Bearer "));
            case AUTH_HEADER:
                return new HeaderAuth(cfg.getOrDefault("header", "Token"),
                        cfg.getOrDefault("token", ""),
                        cfg.getOrDefault("prefix", ""));
            case AUTH_APIKEY:
                return new ApiKeyAuth(cfg.getOrDefault("key_name", "X-Api-Key"),
                        cfg.getOrDefault("token", ""),
                        cfg.getOrDefault("key_in", "header"));
            case AUTH_COOKIE:
                Object cookies = Base.safeLoads(cfg.get("cookies"), null);
                return new CookieAuth(cookies, cfg.getOrDefault("cookie_string", ""));
            case AUTH_OAUTH2:
                Integer timeout = null;
                if (cfg.containsKey("timeout") && !cfg.get("timeout").isEmpty()) {
                    timeout = Integer.parseInt(cfg.get("timeout").trim());
                }
                Boolean verify = null;
                if (cfg.containsKey("verify")) {
                    verify = Base.toBool(cfg.get("verify"), Settings.AuthConfig.TOKEN_VERIFY);
                }
                Integer refreshAhead = null;
                if (cfg.containsKey("refresh_ahead") && !cfg.get("refresh_ahead").isEmpty()) {
                    refreshAhead = Integer.parseInt(cfg.get("refresh_ahead").trim());
                }
                return new OAuth2ClientCredentials(
                        cfg.getOrDefault("token_url", ""),
                        cfg.getOrDefault("client_id", ""),
                        cfg.getOrDefault("client_secret", ""),
                        cfg.getOrDefault("scope", ""),
                        cfg.getOrDefault("header", "Authorization"),
                        cfg.getOrDefault("prefix", "Bearer "),
                        cfg.getOrDefault("auth_mode", "post"),
                        timeout, verify, refreshAhead
                );
            default:
                throw new IllegalArgumentException("不支持的认证类型 '" + kind + "，可选：none, basic, bearer, header, apikey, cookie, oauth2");
        }
    }
}
