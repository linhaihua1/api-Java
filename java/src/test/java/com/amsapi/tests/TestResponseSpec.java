package com.amsapi.tests;

import com.amsapi.common.response.ResponseSpec;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestResponseSpec {

    private Map<String, Object> makeResponse(int httpCode, Object body) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", httpCode);
        res.put("body", body);
        res.put("elapsed", 100.0);
        return res;
    }

    @Test
    void testIsHttpOk() {
        ResponseSpec spec = new ResponseSpec("status", null, null, null, null, null);
        assertTrue(spec.isHttpOk(makeResponse(200, null)));
        assertTrue(spec.isHttpOk(makeResponse(201, null)));
        assertFalse(spec.isHttpOk(makeResponse(500, null)));
        assertFalse(spec.isHttpOk(makeResponse(404, null)));
    }

    @Test
    void testCodeStyleSuccess() {
        ResponseSpec spec = new ResponseSpec("code", null, null, null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        Map<String, Object> res = makeResponse(200, body);
        assertTrue(spec.isSuccess(res, 200));
        assertFalse(spec.isSuccess(res, 500));
    }

    @Test
    void testSuccessStyle() {
        ResponseSpec spec = new ResponseSpec("success", null, null, null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        Map<String, Object> res = makeResponse(200, body);
        assertTrue(spec.isSuccess(res, null));

        body.put("success", false);
        assertFalse(spec.isSuccess(res, null));
    }

    @Test
    void testErrnoStyle() {
        ResponseSpec spec = new ResponseSpec("errno", null, null, null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errno", 0);
        Map<String, Object> res = makeResponse(200, body);
        assertTrue(spec.isSuccess(res, null));

        body.put("errno", 100);
        assertFalse(spec.isSuccess(res, null));
    }

    @Test
    void testRetcodeStyle() {
        ResponseSpec spec = new ResponseSpec("retcode", null, null, null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("retCode", "0000");
        Map<String, Object> res = makeResponse(200, body);
        assertTrue(spec.isSuccess(res, null));

        body.put("retCode", "0001");
        assertFalse(spec.isSuccess(res, null));
    }

    @Test
    void testRawStyle() {
        ResponseSpec spec = new ResponseSpec("raw", null, null, null, null, null);
        Map<String, Object> res = makeResponse(200, "anything");
        // raw 风格只看 HTTP 状态码
        assertTrue(spec.isSuccess(res, null));
    }

    @Test
    void testCustomSuccessPath() {
        ResponseSpec spec = new ResponseSpec("code", "body.data.status", null, null, null, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", 0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        Map<String, Object> res = makeResponse(200, body);
        assertTrue(spec.isSuccess(res, 0));
    }

    @Test
    void testCustomSuccessValues() {
        ResponseSpec spec = new ResponseSpec("code", "body.code", "OK,SUCCESS", null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "OK");
        Map<String, Object> res = makeResponse(200, body);
        assertTrue(spec.isSuccess(res, null));
    }

    @Test
    void testHttpOkNotPass() {
        ResponseSpec spec = new ResponseSpec("code", null, null, null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        Map<String, Object> res = makeResponse(500, body);
        // HTTP 500 即使业务码正确也应失败
        assertFalse(spec.isSuccess(res, 200));
    }

    @Test
    void testMessage() {
        ResponseSpec spec = new ResponseSpec("code", null, null, null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg", "操作成功");
        Map<String, Object> res = makeResponse(200, body);
        assertEquals("操作成功", spec.message(res));
    }

    @Test
    void testDataExtraction() {
        ResponseSpec spec = new ResponseSpec("code", null, null, null, "body.result", null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", Arrays.asList(1, 2, 3));
        Map<String, Object> res = makeResponse(200, body);
        assertEquals(Arrays.asList(1, 2, 3), spec.data(res));
    }

    @Test
    void testEqual() {
        assertTrue(ResponseSpec.equal(200, "200"));
        assertTrue(ResponseSpec.equal("ok", "OK"));
        assertTrue(ResponseSpec.equal(1.0, 1));
        assertFalse(ResponseSpec.equal("a", "b"));
    }

    @Test
    void testDescribe() {
        ResponseSpec spec = new ResponseSpec("success", null, null, null, null, null);
        Map<String, String> desc = spec.describe();
        assertEquals("success", desc.get("判定风格"));
    }

    @Test
    void testActualCode() {
        ResponseSpec spec = new ResponseSpec("code", null, null, null, null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 404);
        Map<String, Object> res = makeResponse(200, body);
        assertEquals(404, spec.actualCode(res));
    }
}
