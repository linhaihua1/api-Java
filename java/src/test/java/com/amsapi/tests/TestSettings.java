package com.amsapi.tests;

import com.amsapi.config.Settings;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestSettings {

    @Test
    void testBaseDirNotNull() {
        assertNotNull(Settings.BASE_DIR);
        assertFalse(Settings.BASE_DIR.isEmpty());
    }

    @Test
    void testConfigDirExists() {
        // config 目录应该存在
        File configDir = new File(Settings.CONFIG_DIR);
        assertTrue(configDir.exists() || new File(Settings.BASE_DIR, "config").exists());
    }

    @Test
    void testRunConfigDefaults() {
        assertNotNull(Settings.RunConfig.DATA_SOURCE);
        assertTrue(Settings.RunConfig.TIMEOUT > 0);
        assertNotNull(Settings.RunConfig.SYSTEM);
    }

    @Test
    void testLogConfig() {
        assertNotNull(Settings.LogConfig.LEVEL);
        assertNotNull(Settings.LogConfig.fileName());
        assertTrue(Settings.LogConfig.fileName().endsWith(".log"));
    }

    @Test
    void testReportConfig() {
        assertNotNull(Settings.ReportConfig.TITLE);
        assertNotNull(Settings.ReportConfig.htmlPath());
    }

    @Test
    void testDbConfigKeys() {
        assertTrue(Settings.DB_CONFIG.containsKey("host"));
        assertTrue(Settings.DB_CONFIG.containsKey("port"));
        assertTrue(Settings.DB_CONFIG.containsKey("user"));
        assertEquals(3306, Settings.DB_CONFIG.get("port"));
    }

    @Test
    void testMissingDbKeys() {
        // 默认配置下应该缺少必填项
        List<String> missing = Settings.missing_db_keys();
        assertNotNull(missing);
    }

    @Test
    void testValidDataSources() {
        assertTrue(Settings.VALID_DATA_SOURCES.contains("excel"));
        assertTrue(Settings.VALID_DATA_SOURCES.contains("yaml"));
        assertTrue(Settings.VALID_DATA_SOURCES.contains("mysql"));
        assertTrue(Settings.VALID_DATA_SOURCES.contains("all"));
    }

    @Test
    void testResolveDataSource() {
        // 不抛异常即可
        String source = Settings.resolve_data_source();
        assertNotNull(source);
    }

    @Test
    void testPathConstants() {
        assertNotNull(Settings.CONFIG_DIR);
        assertNotNull(Settings.DATA_DIR);
        assertNotNull(Settings.LOG_DIR);
        assertNotNull(Settings.REPORT_DIR);
        assertNotNull(Settings.SYSTEMS_DIR);
        assertNotNull(Settings.CASES_DIR);
        assertNotNull(Settings.CONF_FILE);
    }

    @Test
    void testHelperPaths() {
        assertNotNull(Settings.getLogPath());
        assertNotNull(Settings.getReportPath());
        assertNotNull(Settings.getConfPath());
        assertNotNull(Settings.getDataPath());
    }
}
