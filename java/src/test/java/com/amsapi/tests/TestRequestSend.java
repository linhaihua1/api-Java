package com.amsapi.tests;

import com.amsapi.common.Constants;
import com.amsapi.utils.RequestSend;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestRequestSend {

    @Test
    void testBodyLocationGet() {
        assertEquals("params", RequestSend.bodyLocation("get", null));
        assertEquals("params", RequestSend.bodyLocation("GET", null));
    }

    @Test
    void testBodyLocationPostJson() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        assertEquals("json", RequestSend.bodyLocation("post", headers));
    }

    @Test
    void testBodyLocationPostForm() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        assertEquals("data", RequestSend.bodyLocation("post", headers));
    }

    @Test
    void testBodyLocationPostXml() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/xml");
        assertEquals("data", RequestSend.bodyLocation("post", headers));
    }

    @Test
    void testBodyLocationPostDefault() {
        // 无 Content-Type 的 POST 默认为 json
        assertEquals("json", RequestSend.bodyLocation("post", new LinkedHashMap<>()));
    }

    @Test
    void testBodyLocationDelete() {
        assertEquals("params", RequestSend.bodyLocation("delete", new LinkedHashMap<>()));
    }

    @Test
    void testContentType() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        assertTrue(RequestSend.contentType(headers).startsWith("application/json"));
    }

    @Test
    void testContentTypeCaseInsensitive() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", "application/xml");
        assertTrue(RequestSend.contentType(headers).startsWith("application/xml"));
    }

    @Test
    void testContentTypeMissing() {
        assertEquals("", RequestSend.contentType(new LinkedHashMap<>()));
        assertEquals("", RequestSend.contentType(null));
    }

    @Test
    void testConstants() {
        assertEquals("application/json", Constants.CT_JSON);
        assertEquals("application/xml", Constants.CT_XML);
        assertEquals("text/plain", Constants.CT_TEXT);
    }
}
