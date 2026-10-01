package com.amsapi.tests;

import com.amsapi.common.hooks.Hooks;
import com.amsapi.common.base.Base;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestHooks {

    @Test
    void testDigestMd5() {
        // MD5 of "hello"
        String md5 = Hooks.digest("hello", "md5", null);
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5);
    }

    @Test
    void testDigestSha256() {
        String sha = Hooks.digest("hello", "sha256", null);
        assertEquals(64, sha.length()); // SHA-256 hex is 64 chars
    }

    @Test
    void testDigestHmacMd5() {
        String hmac = Hooks.digest("hello", "hmac-md5", "secret");
        assertNotNull(hmac);
        assertEquals(32, hmac.length());
    }

    @Test
    void testSignSorted() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("c", "3");

        String sign = Hooks.signSorted(params, "salt", "md5", false, new String[]{"sign"}, "&", "=", true);
        assertNotNull(sign);
        assertEquals(32, sign.length());
    }

    @Test
    void testSplitTopLevel() {
        List<String> parts = Hooks.splitTopLevel("a,b,c", ',');
        assertEquals(3, parts.size());
        assertEquals("a", parts.get(0));
    }

    @Test
    void testSplitTopLevelWithBrackets() {
        List<String> parts = Hooks.splitTopLevel("func({a:1}),func2()", ',');
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).contains("{a:1}"));
    }

    @Test
    void testAesEncryptDecrypt() throws Exception {
        String key = "0123456789abcdef"; // 16 bytes for AES-128
        String plain = "hello world";

        String encrypted = Hooks.aesEncrypt(plain, key, null, "ECB", true);
        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);

        String decrypted = Hooks.aesDecrypt(encrypted, key, null, "ECB", true);
        assertEquals(plain, decrypted);
    }

    @Test
    void testMakeTimestampNonceHook() {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("fields", Arrays.asList("ts", "nonce"));
        kwargs.put("ts_unit", "s");

        java.util.function.Function<Map<String, Object>, Map<String, Object>> hook = Hooks.makeTimestampNonceHook(kwargs);
        Map<String, Object> ctx = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        ctx.put("payload", payload);

        Map<String, Object> result = hook.apply(ctx);
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) result.get("payload");
        assertTrue(p.containsKey("ts"));
        assertTrue(p.containsKey("nonce"));
        assertNotNull(p.get("ts"));
        assertTrue(((String) p.get("nonce")).length() > 0);
    }

    @Test
    void testMakeSignHook() {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("field", "sign");
        kwargs.put("alg", "md5");

        java.util.function.Function<Map<String, Object>, Map<String, Object>> hook = Hooks.makeSignHook(kwargs);
        Map<String, Object> ctx = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a", "1");
        payload.put("b", "2");
        ctx.put("payload", payload);

        Map<String, Object> result = hook.apply(ctx);
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) result.get("payload");
        assertTrue(p.containsKey("sign"));
        assertEquals(32, ((String) p.get("sign")).length());
    }

    @Test
    void testHookRegistry() {
        // 无钩子时不应报错
        Hooks.HookRegistry registry = new Hooks.HookRegistry(null, null);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("payload", new LinkedHashMap<>());

        Map<String, Object> before = registry.runBefore(ctx);
        assertNotNull(before);

        Map<String, Object> after = registry.runAfter(ctx);
        assertNotNull(after);

        assertFalse(registry.hasBefore());
        assertFalse(registry.hasAfter());
    }

    @Test
    void testMakeAesHook() throws Exception {
        String key = "0123456789abcdef";
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("key", key);
        kwargs.put("fields", Arrays.asList("data"));

        java.util.function.Function<Map<String, Object>, Map<String, Object>> hook = Hooks.makeAesHook(kwargs);
        Map<String, Object> ctx = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("data", "hello");
        ctx.put("payload", payload);

        Map<String, Object> result = hook.apply(ctx);
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) result.get("payload");
        // data 应该被加密成 base64
        String encrypted = (String) p.get("data");
        assertNotNull(encrypted);
        assertNotEquals("hello", encrypted);

        // 验证可以解密回来
        String decrypted = Hooks.aesDecrypt(encrypted, key, null, "ECB", true);
        assertEquals("hello", decrypted);
    }
}
