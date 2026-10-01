package com.amsapi.tests;

import com.amsapi.common.base.Base;
import com.amsapi.common.variable.GlobalVar;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 框架核心能力单元测试。
 * 对应 Python 版 tests/ 下的自测套件。
 */
public class CoreTest {

    @Test
    void testGlobalVar() {
        GlobalVar.clear();
        GlobalVar.set("ticket", "abc123");
        assertEquals("abc123", GlobalVar.get("ticket"));
        assertNull(GlobalVar.get("nonexistent"));
        GlobalVar.clear();
    }

    @Test
    void testFindAndReplace() {
        GlobalVar.clear();
        GlobalVar.set("name", "world");
        String text = "hello ${name}";
        List<String> found = Base.find(text);
        assertEquals(1, found.size());
        assertEquals("name", found.get(0));
        String replaced = Base.replace(text);
        assertEquals("hello world", replaced);
        GlobalVar.clear();
    }

    @Test
    void testParseRelation() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", "T123");
        body.put("data", data);
        body.put("status", 200);
        response.put("body", body);
        response.put("code", 200);

        Object val = Base.parseRelation(Arrays.asList("body", "data", "ticket"), response);
        assertEquals("T123", val);

        Object status = Base.parseRelation(Arrays.asList("body", "status"), response);
        assertEquals(200, status);
    }

    @Test
    void testSafeLoads() {
        Object result = Base.safeLoads("{\"key\": \"value\"}", null);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals("value", ((Map<?, ?>) result).get("key"));
    }

    @Test
    void testToBool() {
        assertTrue(Base.toBool("true", false));
        assertTrue(Base.toBool("1", false));
        assertFalse(Base.toBool("false", true));
        assertTrue(Base.toBool(null, true));
    }

    @Test
    void testParseTags() {
        assertEquals(Arrays.asList("smoke"), Base.parseTags("smoke"));
        assertEquals(Arrays.asList("smoke", "core"), Base.parseTags("smoke,core"));
        assertTrue(Base.parseTags(null).isEmpty());
    }

    @Test
    void testXmlToDict() {
        String xml = "<root><code>200</code><msg>ok</msg></root>";
        Map<String, Object> result = Base.xmlToDict(xml);
        assertNotNull(result);
        assertTrue(result.containsKey("root"));
        Map<String, Object> root = (Map<String, Object>) result.get("root");
        assertEquals(200, root.get("code"));
        assertEquals("ok", root.get("msg"));
    }
}
