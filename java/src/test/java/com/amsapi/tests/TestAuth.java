package com.amsapi.tests;

import com.amsapi.common.auth.*;
import com.amsapi.common.variable.GlobalVar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestAuth {

    @BeforeEach
    void setUp() {
        GlobalVar.clear();
    }

    @AfterEach
    void tearDown() {
        GlobalVar.clear();
    }

    private Map<String, String> h() { return new LinkedHashMap<>(); }
    private Map<String, String> c() { return new LinkedHashMap<>(); }
    private Map<String, Object> p() { return new LinkedHashMap<>(); }

    @Test
    void testNoAuth() {
        NoAuth auth = new NoAuth();
        assertEquals("none", auth.name());
        Map<String, String> headers = h();
        auth.apply(headers, c(), p());
        assertTrue(headers.isEmpty());
    }

    @Test
    void testBasicAuth() {
        BasicAuth auth = new BasicAuth("user", "pass");
        assertEquals("basic", auth.name());
        Map<String, String> headers = h();
        auth.apply(headers, c(), p());
        String value = headers.get("Authorization");
        assertNotNull(value);
        assertTrue(value.startsWith("Basic "));
        // user:pass -> base64
        String expected = java.util.Base64.getEncoder()
                .encodeToString("user:pass".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Basic " + expected, value);
    }

    @Test
    void testBearerAuth() {
        BearerAuth auth = new BearerAuth("mytoken", "Authorization", "Bearer ");
        Map<String, String> headers = h();
        auth.apply(headers, c(), p());
        assertEquals("Bearer mytoken", headers.get("Authorization"));
    }

    @Test
    void testBearerWithVariable() {
        GlobalVar.set("token", "dynamic-token");
        BearerAuth auth = new BearerAuth("${token}", "Authorization", "Bearer ");
        Map<String, String> headers = h();
        auth.apply(headers, c(), p());
        assertEquals("Bearer dynamic-token", headers.get("Authorization"));
    }

    @Test
    void testHeaderAuth() {
        HeaderAuth auth = new HeaderAuth("X-Token", "abc", "");
        Map<String, String> headers = h();
        auth.apply(headers, c(), p());
        assertEquals("abc", headers.get("X-Token"));
    }

    @Test
    void testApiKeyHeader() {
        ApiKeyAuth auth = new ApiKeyAuth("X-Api-Key", "secret", "header");
        Map<String, String> headers = h();
        Map<String, Object> params = p();
        auth.apply(headers, c(), params);
        assertEquals("secret", headers.get("X-Api-Key"));
        assertTrue(params.isEmpty());
    }

    @Test
    void testApiKeyQuery() {
        ApiKeyAuth auth = new ApiKeyAuth("api_key", "secret", "query");
        Map<String, String> headers = h();
        Map<String, Object> params = p();
        auth.apply(headers, c(), params);
        assertEquals("secret", params.get("api_key"));
        assertTrue(headers.isEmpty());
    }

    @Test
    void testCookieAuthString() {
        CookieAuth auth = new CookieAuth(null, "JSESSIONID=abc; token=xyz");
        Map<String, String> cookies = c();
        auth.apply(h(), cookies, p());
        assertEquals("abc", cookies.get("JSESSIONID"));
        assertEquals("xyz", cookies.get("token"));
    }

    @Test
    void testCookieAuthMap() {
        Map<String, Object> cookieMap = new LinkedHashMap<>();
        cookieMap.put("session", "s123");
        CookieAuth auth = new CookieAuth(cookieMap, null);
        Map<String, String> cookies = c();
        auth.apply(h(), cookies, p());
        assertEquals("s123", cookies.get("session"));
    }

    @Test
    void testAuthFactoryNoAuth() {
        Map<String, String> cfg = new LinkedHashMap<>();
        cfg.put("type", "none");
        AuthStrategy auth = AuthFactory.build(cfg);
        assertTrue(auth instanceof NoAuth);
    }

    @Test
    void testAuthFactoryBasic() {
        Map<String, String> cfg = new LinkedHashMap<>();
        cfg.put("type", "basic");
        cfg.put("username", "admin");
        cfg.put("password", "123");
        AuthStrategy auth = AuthFactory.build(cfg);
        assertTrue(auth instanceof BasicAuth);
    }

    @Test
    void testAuthFactoryBearer() {
        Map<String, String> cfg = new LinkedHashMap<>();
        cfg.put("type", "bearer");
        cfg.put("token", "${token}");
        AuthStrategy auth = AuthFactory.build(cfg);
        assertTrue(auth instanceof BearerAuth);
    }

    @Test
    void testAuthFactoryDefault() {
        AuthStrategy auth = AuthFactory.build(new LinkedHashMap<>());
        assertTrue(auth instanceof NoAuth);
    }

    @Test
    void testAuthFactoryUnknownType() {
        Map<String, String> cfg = new LinkedHashMap<>();
        cfg.put("type", "unknown");
        assertThrows(IllegalArgumentException.class, () -> AuthFactory.build(cfg));
    }

    @Test
    void testAuthDescribe() {
        BasicAuth auth = new BasicAuth("user", "pass");
        Map<String, String> desc = auth.describe();
        assertEquals("basic", desc.get("认证方式"));
        assertEquals("user", desc.get("用户名"));
    }
}
