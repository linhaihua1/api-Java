package com.amsapi.tests;

import com.amsapi.common.asserts.Asserts;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestAsserts {

    private Map<String, Object> makeResponse(Object body, int code) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", code);
        res.put("body", body);
        res.put("elapsed", 150.0);
        return res;
    }

    private Map<String, Object> sampleResponse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", 1);
        data.put("name", "test");
        data.put("active", true);
        data.put("tags", Arrays.asList("a", "b", "c"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("msg", "success");
        body.put("data", data);
        return makeResponse(body, 200);
    }

    // ---- equals / not_equals ----
    @Test
    void testEquals() {
        assertTrue(Asserts.equals(sampleResponse(), "body.code", 200, ""));
        assertThrows(AssertionError.class,
                () -> Asserts.equals(sampleResponse(), "body.code", 500, ""));
    }

    @Test
    void testNotEquals() {
        assertTrue(Asserts.notEquals(sampleResponse(), "body.code", 500, ""));
        assertThrows(AssertionError.class,
                () -> Asserts.notEquals(sampleResponse(), "body.code", 200, ""));
    }

    // ---- contains / not_contains ----
    @Test
    void testContainsString() {
        assertTrue(Asserts.contains(sampleResponse(), "body.msg", "success", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.contains(sampleResponse(), "body.msg", "fail", ""));
    }

    @Test
    void testContainsList() {
        assertTrue(Asserts.contains(sampleResponse(), "body.data.tags", "a", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.contains(sampleResponse(), "body.data.tags", "z", ""));
    }

    @Test
    void testNotContains() {
        assertTrue(Asserts.notContains(sampleResponse(), "body.data.tags", "z", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.notContains(sampleResponse(), "body.data.tags", "a", ""));
    }

    // ---- startswith / endswith ----
    @Test
    void testStartsWith() {
        assertTrue(Asserts.startsWith(sampleResponse(), "body.msg", "suc", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.startsWith(sampleResponse(), "body.msg", "fail", ""));
    }

    @Test
    void testEndsWith() {
        assertTrue(Asserts.endsWith(sampleResponse(), "body.msg", "ess", ""));
    }

    // ---- regex ----
    @Test
    void testRegex() {
        assertTrue(Asserts.regex(sampleResponse(), "body.msg", "suc.*", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.regex(sampleResponse(), "body.msg", "^[0-9]+$", ""));
    }

    // ---- type ----
    @Test
    void testType() {
        assertTrue(Asserts.typeIs(sampleResponse(), "body.data", "dict", ""));
        assertTrue(Asserts.typeIs(sampleResponse(), "body.data.id", "int", ""));
        assertTrue(Asserts.typeIs(sampleResponse(), "body.data.tags", "list", ""));
        assertTrue(Asserts.typeIs(sampleResponse(), "body.data.active", "bool", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.typeIs(sampleResponse(), "body.data.id", "str", ""));
    }

    // ---- exists / not_exists ----
    @Test
    void testExists() {
        assertTrue(Asserts.exists(sampleResponse(), "body.data.name", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.exists(sampleResponse(), "body.data.nonexistent", ""));
    }

    @Test
    void testNotExists() {
        assertTrue(Asserts.notExists(sampleResponse(), "body.data.nonexistent", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.notExists(sampleResponse(), "body.data.name", ""));
    }

    // ---- in / not_in ----
    @Test
    void testIn() {
        assertTrue(Asserts.in(sampleResponse(), "body.data.id", "1,2,3", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.in(sampleResponse(), "body.data.id", "5,6,7", ""));
    }

    @Test
    void testNotIn() {
        assertTrue(Asserts.notIn(sampleResponse(), "body.data.id", "5,6,7", ""));
    }

    // ---- gt / ge / lt / le ----
    @Test
    void testComparisons() {
        assertTrue(Asserts.gt(sampleResponse(), "body.data.id", 0, ""));
        assertTrue(Asserts.ge(sampleResponse(), "body.data.id", 1, ""));
        assertTrue(Asserts.lt(sampleResponse(), "body.data.id", 100, ""));
        assertTrue(Asserts.le(sampleResponse(), "body.data.id", 1, ""));
        assertThrows(AssertionError.class,
                () -> Asserts.gt(sampleResponse(), "body.data.id", 100, ""));
    }

    // ---- length ----
    @Test
    void testLength() {
        assertTrue(Asserts.length(sampleResponse(), "body.data.tags", 3, ""));
        assertThrows(AssertionError.class,
                () -> Asserts.length(sampleResponse(), "body.data.tags", 5, ""));
    }

    // ---- empty / not_empty ----
    @Test
    void testEmpty() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("empty", "");
        Map<String, Object> res = makeResponse(body, 200);
        assertTrue(Asserts.empty(res, "body.empty", ""));
    }

    @Test
    void testNotEmpty() {
        assertTrue(Asserts.notEmpty(sampleResponse(), "body.data.name", ""));
        assertThrows(AssertionError.class,
                () -> Asserts.notEmpty(sampleResponse(), "body.nonexistent", ""));
    }

    // ---- elapsed ----
    @Test
    void testElapsed() {
        assertTrue(Asserts.elapsed(sampleResponse(), null, 1000, ""));
        assertThrows(AssertionError.class,
                () -> Asserts.elapsed(sampleResponse(), null, 50, ""));
    }

    // ---- runOp dispatch ----
    @Test
    void testRunOp() {
        assertTrue(Asserts.runOp("equals", sampleResponse(), "body.code", 200, ""));
        assertTrue(Asserts.runOp("eq", sampleResponse(), "body.code", 200, ""));
        assertTrue(Asserts.runOp("contains", sampleResponse(), "body.msg", "success", ""));
        assertTrue(Asserts.runOp("gt", sampleResponse(), "body.data.id", 0, ""));
    }

    @Test
    void testRunOpUnknown() {
        assertThrows(AssertionError.class,
                () -> Asserts.runOp("unknown_op", sampleResponse(), "body", null, ""));
    }

    // ---- valueOf ----
    @Test
    void testValueOf() {
        assertEquals(200, Asserts.valueOf(sampleResponse(), "body.code", null));
        assertEquals("default", Asserts.valueOf(sampleResponse(), "body.nonexistent", "default"));
    }

    // ---- baseEquals ----
    @Test
    void testBaseEquals() {
        assertTrue(Asserts.baseEquals(200, "200"));
        assertTrue(Asserts.baseEquals("ok", "OK"));
        assertFalse(Asserts.baseEquals("a", "b"));
        assertTrue(Asserts.baseEquals(null, null));
        assertFalse(Asserts.baseEquals(null, "a"));
    }
}
