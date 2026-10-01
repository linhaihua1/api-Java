package com.amsapi.tests;

import com.amsapi.common.variable.GlobalVar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestGlobalVar {

    @BeforeEach
    void setUp() {
        GlobalVar.clear();
    }

    @AfterEach
    void tearDown() {
        GlobalVar.clear();
    }

    @Test
    void testSetAndGet() {
        GlobalVar.set("key", "value");
        assertEquals("value", GlobalVar.get("key"));
    }

    @Test
    void testGetWithDefault() {
        assertNull(GlobalVar.get("nonexistent"));
        assertEquals("default", GlobalVar.get("nonexistent", "default"));
    }

    @Test
    void testUpdate() {
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", "hello");
        GlobalVar.update(map);
        assertEquals(1, GlobalVar.get("a"));
        assertEquals("hello", GlobalVar.get("b"));
    }

    @Test
    void testHas() {
        GlobalVar.set("exists", true);
        assertTrue(GlobalVar.has("exists"));
        assertFalse(GlobalVar.has("notexists"));
    }

    @Test
    void testClear() {
        GlobalVar.set("k1", "v1");
        GlobalVar.set("k2", "v2");
        GlobalVar.clear();
        assertNull(GlobalVar.get("k1"));
        assertNull(GlobalVar.get("k2"));
    }

    @Test
    void testAll() {
        GlobalVar.set("x", 1);
        GlobalVar.set("y", 2);
        Map<String, Object> all = GlobalVar.all();
        assertEquals(2, all.size());
        assertEquals(1, all.get("x"));
    }

    @Test
    void testSetReturnsValue() {
        Object result = GlobalVar.set("ret", 42);
        assertEquals(42, result);
    }

    @Test
    void testOverwrite() {
        GlobalVar.set("k", "old");
        GlobalVar.set("k", "new");
        assertEquals("new", GlobalVar.get("k"));
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        Runnable writer = () -> {
            for (int i = 0; i < 100; i++) {
                GlobalVar.set("key" + i, i);
            }
        };
        Thread t1 = new Thread(writer);
        Thread t2 = new Thread(writer);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertEquals(0, GlobalVar.get("key0"));
        assertEquals(99, GlobalVar.get("key99"));
    }
}
