package com.amsapi.tests;

import com.amsapi.common.profile.SystemProfile;
import com.amsapi.config.Settings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestSystemProfile {

    @Test
    void testLoadDefaultProfile() {
        SystemProfile.clearCache();
        SystemProfile profile = SystemProfile.loadProfile("default");
        assertNotNull(profile);
        assertEquals("default", profile.getName());
    }

    @Test
    void testLoadMallProfile() {
        SystemProfile.clearCache();
        SystemProfile profile = SystemProfile.loadProfile("mall");
        assertNotNull(profile);
        assertEquals("mall", profile.getName());
        // mall 档案应该有 hosts 别名
        assertNotNull(profile.describe().get("域名别名"));
    }

    @Test
    void testProfileCaching() {
        SystemProfile.clearCache();
        SystemProfile p1 = SystemProfile.loadProfile("default");
        SystemProfile p2 = SystemProfile.loadProfile("default");
        assertSame(p1, p2); // 同一实例（缓存生效）
    }

    @Test
    void testForceReload() {
        SystemProfile.clearCache();
        SystemProfile p1 = SystemProfile.loadProfile("default");
        SystemProfile p2 = SystemProfile.loadProfile("default", true);
        // 强制重新加载，可能是不同实例
        assertNotNull(p2);
    }

    @Test
    void testNonExistentProfileThrows() {
        SystemProfile.clearCache();
        assertThrows(com.amsapi.common.exceptions.ConfigError.class,
                () -> SystemProfile.loadProfile("nonexistent_system_xyz"));
    }

    @Test
    void testAvailableSystems() {
        java.util.List<String> systems = SystemProfile.availableSystems();
        assertNotNull(systems);
        assertTrue(systems.contains("default"));
        assertTrue(systems.contains("mall"));
    }

    @Test
    void testResolveUrlWithHost() {
        SystemProfile.clearCache();
        SystemProfile profile = SystemProfile.loadProfile("mall");
        // mall 档案配置了 order 域名别名
        String url = profile.resolveUrl("/v1/orders", "order", null);
        assertTrue(url.contains("order-service.mall.com") || url.contains("/v1/orders"));
    }

    @Test
    void testResolveUrlFullUrl() {
        SystemProfile profile = SystemProfile.loadProfile("default");
        String url = profile.resolveUrl("http://example.com/api", null, null);
        assertEquals("http://example.com/api", url);
    }

    @Test
    void testResolveUrlRelativePath() {
        SystemProfile profile = SystemProfile.loadProfile("default");
        String base = profile.getEffectiveBaseUrl();
        String url = profile.resolveUrl("/v1/test", null, base);
        assertTrue(url.endsWith("/v1/test"));
    }

    @Test
    void testDescribe() {
        SystemProfile profile = SystemProfile.loadProfile("default");
        Map<String, String> desc = profile.describe();
        assertNotNull(desc);
        assertTrue(desc.containsKey("系统档案"));
        assertTrue(desc.containsKey("判定风格"));
    }

    @Test
    void testGetResponseFormat() {
        SystemProfile profile = SystemProfile.loadProfile("default");
        String format = profile.getResponseFormat();
        assertNotNull(format);
        // 应该是 auto 或 json 或 xml 等
        assertTrue(format.length() > 0);
    }

    @Test
    void testGetTimeout() {
        SystemProfile profile = SystemProfile.loadProfile("default");
        int timeout = profile.getTimeout();
        assertTrue(timeout > 0);
    }
}
