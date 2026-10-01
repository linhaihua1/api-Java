package com.amsapi.tests;

import com.amsapi.config.IniParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestIniParser {

    @TempDir
    Path tempDir;

    private void write(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testReadSimpleIni() throws IOException {
        Path file = tempDir.resolve("test.ini");
        write(file, "[section1]\nkey1 = value1\nkey2=value2\n\n[section2]\nfoo = bar\n");

        Map<String, Map<String, String>> result = IniParser.read(file);
        assertEquals(2, result.size());
        assertEquals("value1", result.get("section1").get("key1"));
        assertEquals("value2", result.get("section1").get("key2"));
        assertEquals("bar", result.get("section2").get("foo"));
    }

    @Test
    void testCommentsAndBlankLines() throws IOException {
        Path file = tempDir.resolve("test.ini");
        write(file, "; this is a comment\n# another comment\n\n[sec]\n; inline\nkey = val\n");

        Map<String, Map<String, String>> result = IniParser.read(file);
        assertEquals(1, result.size());
        assertEquals("val", result.get("sec").get("key"));
    }

    @Test
    void testPreservesCase() throws IOException {
        Path file = tempDir.resolve("test.ini");
        write(file, "[headers]\nContent-Type = application/json\nX-Token = abc\n");

        Map<String, Map<String, String>> result = IniParser.read(file);
        assertNotNull(result.get("headers").get("Content-Type"));
        assertNotNull(result.get("headers").get("X-Token"));
    }

    @Test
    void testNonExistentFile() {
        Map<String, Map<String, String>> result = IniParser.read(new File("nonexistent.ini").toPath());
        assertTrue(result.isEmpty());
    }

    @Test
    void testLowerKeys() {
        Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("Key1", "val1");
        data.put("KEY2", "val2");

        Map<String, String> lowered = IniParser.lowerKeys(data);
        assertEquals("val1", lowered.get("key1"));
        assertEquals("val2", lowered.get("key2"));
    }

    @Test
    void testValueWithSpaces() throws IOException {
        Path file = tempDir.resolve("test.ini");
        write(file, "[sec]\n  key  =  value with spaces  \n");

        Map<String, Map<String, String>> result = IniParser.read(file);
        assertEquals("value with spaces", result.get("sec").get("key"));
    }

    @Test
    void testEmptyValue() throws IOException {
        Path file = tempDir.resolve("test.ini");
        write(file, "[sec]\nkey =\n");

        Map<String, Map<String, String>> result = IniParser.read(file);
        assertEquals("", result.get("sec").get("key"));
    }
}
