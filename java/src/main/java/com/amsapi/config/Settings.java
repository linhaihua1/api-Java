package com.amsapi.config;

import java.io.File;
import java.util.*;

/**
 * 配置中心。
 * 对应 Python 版 config/settings.py。
 *
 * 统一管理：项目路径、运行开关、环境信息、数据库、日志、报告、认证、CI 判定。
 * 本文件是「可配置」的落点：框架内不允许再出现硬编码的业务数据。
 *
 * 优先级：环境变量（AMSAPI_*） > config/conf.ini > 本文件默认值
 *
 * 配置分组（与 conf.ini 的段一一对应）：
 *   RunConfig     运行开关         [env] [run]
 *   DB_CONFIG     MySQL 连接       [db]
 *   ReportConfig  报告标题/文件名  [report]
 *   LogConfig     日志级别/格式    [log]
 *   AuthConfig    OAuth2 取令牌    [auth]
 *   CIConfig      CI 环境判定      [ci]
 */
public final class Settings {

    /** 环境变量前缀：所有可被覆盖的配置项均以该前缀暴露给 CI/CD */
    public static final String ENV_PREFIX = "AMSAPI_";

    /** 被 AMSAPI_* 环境变量显式覆盖过的配置项名（大写） */
    public static final Set<String> ENV_OVERRIDES = new HashSet<>();

    // --------------------------------------------------------------------------
    // 一、目录 / 文件路径（唯一来源）
    // --------------------------------------------------------------------------
    /** 项目根目录：可通过系统属性 amsapi.base.dir 或环境变量指定 */
    public static final String BASE_DIR;
    public static final String CONFIG_DIR;
    public static final String DATA_DIR;
    public static final String LOG_DIR;
    public static final String REPORT_DIR;
    public static final String SYSTEMS_DIR;
    public static final String CASES_DIR;
    public static final String EXCEL_FILE;
    public static final String CONF_FILE;
    public static final String ALLURE_RESULTS_DIR;
    public static final String ALLURE_REPORT_DIR;

    static {
        String baseDir = System.getProperty("amsapi.base.dir");
        if (baseDir == null || baseDir.isEmpty()) {
            baseDir = System.getenv("AMSAPI_BASE_DIR");
        }
        if (baseDir == null || baseDir.isEmpty()) {
            baseDir = System.getProperty("user.dir");
        }
        // 如果在 target/classes 下运行，回退到项目根
        File f = new File(baseDir);
        if (f.getName().equals("classes") && f.getParentFile() != null
                && f.getParentFile().getName().equals("target")) {
            baseDir = f.getParentFile().getParent();
        }
        BASE_DIR = baseDir;

        CONFIG_DIR = join(BASE_DIR, "config");
        DATA_DIR = envOr(ENV_PREFIX + "DATA_DIR", join(BASE_DIR, "data"));
        LOG_DIR = envOr(ENV_PREFIX + "LOG_DIR", join(BASE_DIR, "log"));
        REPORT_DIR = envOr(ENV_PREFIX + "REPORT_DIR", join(BASE_DIR, "report"));
        SYSTEMS_DIR = envOr(ENV_PREFIX + "SYSTEMS_DIR", join(CONFIG_DIR, "systems"));
        CASES_DIR = envOr(ENV_PREFIX + "CASES_DIR", join(DATA_DIR, "cases"));
        EXCEL_FILE = envOr(ENV_PREFIX + "EXCEL_FILE", join(DATA_DIR, "api_tables.xlsx"));

        String confFile = System.getenv(ENV_PREFIX + "CONF_FILE");
        CONF_FILE = (confFile != null && !confFile.isEmpty()) ? confFile : join(CONFIG_DIR, "conf.ini");

        ALLURE_RESULTS_DIR = join(REPORT_DIR, "allure-results");
        ALLURE_REPORT_DIR = join(REPORT_DIR, "allure-report");

        // 确保运行期目录存在
        mkdirs(LOG_DIR, REPORT_DIR, CASES_DIR, SYSTEMS_DIR);
    }

    // --------------------------------------------------------------------------
    // 二、运行开关
    // --------------------------------------------------------------------------
    public static final class RunConfig {
        public static String DATA_SOURCE = "excel";
        public static String SHEET_NAME = "test_case_list";
        public static boolean WRITE_BACK = true;
        public static int TIMEOUT = 10;
        public static boolean VERIFY = false;
        public static String BASE_URL = "";
        public static String ENV = "test";
        public static String SYSTEM = "default";
        public static String DB_WEB = "";
        public static String DB_ENVIRONMENT = "";
        public static String REPORT_TYPE = "allure";
        public static boolean SMOKE_ONLY = false;
        public static String TAGS = "smoke";

        private RunConfig() {
        }
    }

    // --------------------------------------------------------------------------
    // 数据源 -> 用例驱动 marker 的映射
    // --------------------------------------------------------------------------
    public static final Map<String, String> DATA_SOURCE_MARKERS;
    public static final String DATA_SOURCE_ALL = "all";
    public static final List<String> VALID_DATA_SOURCES = Arrays.asList("excel", "yaml", "mysql", DATA_SOURCE_ALL);

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("excel", "excel");
        m.put("yaml", "yaml");
        m.put("mysql", "mysql");
        DATA_SOURCE_MARKERS = Collections.unmodifiableMap(m);
    }

    public static String resolve_data_source() {
        return (RunConfig.DATA_SOURCE == null ? "" : RunConfig.DATA_SOURCE).trim().toLowerCase();
    }

    public static String data_source_marker() {
        String source = resolve_data_source();
        if (source.isEmpty() || DATA_SOURCE_ALL.equals(source)) {
            return null;
        }
        return DATA_SOURCE_MARKERS.get(source);
    }

    // --------------------------------------------------------------------------
    // 三、数据库配置（可选数据源，默认不启用）
    // --------------------------------------------------------------------------
    public static final Map<String, Object> DB_CONFIG = new LinkedHashMap<>();
    public static final List<String> DB_REQUIRED_KEYS = Arrays.asList("host", "user", "database");

    static {
        DB_CONFIG.put("host", "");
        DB_CONFIG.put("user", "");
        DB_CONFIG.put("password", "");
        DB_CONFIG.put("database", "");
        DB_CONFIG.put("port", 3306);
        DB_CONFIG.put("charset", "utf8");
    }

    public static List<String> missing_db_keys() {
        List<String> missing = new ArrayList<>();
        for (String key : DB_REQUIRED_KEYS) {
            Object val = DB_CONFIG.get(key);
            if (val == null || String.valueOf(val).trim().isEmpty()) {
                missing.add(key);
            }
        }
        return missing;
    }

    // --------------------------------------------------------------------------
    // 四、日志 / 报告 / 认证 / CI 可配置项
    // --------------------------------------------------------------------------
    public static final class LogConfig {
        public static String LEVEL = "DEBUG";
        public static String CONSOLE_LEVEL = "DEBUG";
        public static String FILE_NAME_FORMAT = "%Y_%m_%d";
        public static String FORMAT = "%(asctime)s | %(levelname)-7s | %(filename)s:%(lineno)d | %(message)s";
        public static String DATE_FORMAT = "%Y-%m-%d %H:%M:%S";
        public static String ROOT_NAME = "amsapi";

        private LogConfig() {
        }

        public static String fileName() {
            java.time.LocalDate now = java.time.LocalDate.now();
            return String.format("%04d_%02d_%02d.log", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        }
    }

    public static final class ReportConfig {
        public static String TITLE = "接口自动化测试";
        public static String PROJECT = "amsapi-auto";
        public static String HTML_NAME = "report.html";
        public static int MAX_ATTACH_LEN = 20000;

        private ReportConfig() {
        }

        public static String htmlPath() {
            return join(REPORT_DIR, HTML_NAME);
        }
    }

    public static final class AuthConfig {
        public static int REFRESH_AHEAD = 60;
        public static int TOKEN_TIMEOUT = 10;
        public static boolean TOKEN_VERIFY = false;

        private AuthConfig() {
        }
    }

    public static final class CIConfig {
        public static volatile String[] DETECT_ENV_VARS = {
                "JENKINS_URL", "JENKINS_HOME", "GITLAB_CI", "BUILD_NUMBER",
                "GITHUB_ACTIONS", "TF_BUILD", "CI"
        };

        private CIConfig() {
        }
    }

    // --------------------------------------------------------------------------
    // 加载逻辑
    // --------------------------------------------------------------------------
    static {
        loadConfIni();
        loadEnv();
    }

    private static boolean toBool(Object value) {
        return value != null && String.valueOf(value).trim().toLowerCase()
                .matches("1|true|yes|on|y");
    }

    private static void loadConfIni() {
        File file = new File(CONF_FILE);
        if (!file.exists()) {
            return;
        }
        Map<String, Map<String, String>> cf = IniParser.read(file.toPath());

        Map<String, String> run = cf.get("run");
        if (run != null) {
            if (run.containsKey("data_source")) RunConfig.DATA_SOURCE = run.get("data_source");
            if (run.containsKey("sheet_name")) RunConfig.SHEET_NAME = run.get("sheet_name");
            if (run.containsKey("write_back")) RunConfig.WRITE_BACK = toBool(run.get("write_back"));
            if (run.containsKey("timeout")) RunConfig.TIMEOUT = Integer.parseInt(run.get("timeout").trim());
            if (run.containsKey("verify")) RunConfig.VERIFY = toBool(run.get("verify"));
            if (run.containsKey("smoke")) RunConfig.SMOKE_ONLY = toBool(run.get("smoke"));
            if (run.containsKey("tags")) RunConfig.TAGS = run.get("tags");
        }

        Map<String, String> env = cf.get("env");
        if (env != null) {
            if (env.containsKey("env")) RunConfig.ENV = env.get("env");
            if (env.containsKey("base_url")) RunConfig.BASE_URL = env.get("base_url").trim();
            if (env.containsKey("system")) {
                String s = env.get("system").trim();
                RunConfig.SYSTEM = s.isEmpty() ? "default" : s;
            }
        }

        Map<String, String> report = cf.get("report");
        if (report != null) {
            if (report.containsKey("report_type")) RunConfig.REPORT_TYPE = report.get("report_type").trim().toLowerCase();
            if (report.containsKey("title")) {
                String v = report.get("title").trim();
                if (!v.isEmpty()) ReportConfig.TITLE = v;
            }
            if (report.containsKey("project")) {
                String v = report.get("project").trim();
                if (!v.isEmpty()) ReportConfig.PROJECT = v;
            }
            if (report.containsKey("html_name")) {
                String v = report.get("html_name").trim();
                if (!v.isEmpty()) ReportConfig.HTML_NAME = v;
            }
            if (report.containsKey("max_attach_len")) ReportConfig.MAX_ATTACH_LEN = Integer.parseInt(report.get("max_attach_len").trim());
        }

        Map<String, String> log = cf.get("log");
        if (log != null) {
            if (log.containsKey("level")) {
                String v = log.get("level").trim().toUpperCase();
                if (!v.isEmpty()) LogConfig.LEVEL = v;
            }
            if (log.containsKey("console_level")) {
                String v = log.get("console_level").trim().toUpperCase();
                if (!v.isEmpty()) LogConfig.CONSOLE_LEVEL = v;
            }
            if (log.containsKey("file_name_format")) LogConfig.FILE_NAME_FORMAT = log.get("file_name_format");
            if (log.containsKey("format")) LogConfig.FORMAT = log.get("format");
            if (log.containsKey("date_format")) LogConfig.DATE_FORMAT = log.get("date_format");
            if (log.containsKey("root_name")) {
                String v = log.get("root_name").trim();
                if (!v.isEmpty()) LogConfig.ROOT_NAME = v;
            }
        }

        Map<String, String> auth = cf.get("auth");
        if (auth != null) {
            if (auth.containsKey("refresh_ahead")) AuthConfig.REFRESH_AHEAD = Integer.parseInt(auth.get("refresh_ahead").trim());
            if (auth.containsKey("token_timeout")) AuthConfig.TOKEN_TIMEOUT = Integer.parseInt(auth.get("token_timeout").trim());
            if (auth.containsKey("token_verify")) AuthConfig.TOKEN_VERIFY = toBool(auth.get("token_verify"));
        }

        Map<String, String> ci = cf.get("ci");
        if (ci != null && ci.containsKey("detect_env_vars")) {
            String raw = ci.get("detect_env_vars");
            List<String> vars = new ArrayList<>();
            for (String item : raw.split(",")) {
                if (!item.trim().isEmpty()) vars.add(item.trim());
            }
            CIConfig.DETECT_ENV_VARS = vars.toArray(new String[0]);
        }

        Map<String, String> db = cf.get("db");
        if (db != null) {
            for (String key : DB_CONFIG.keySet()) {
                if (db.containsKey(key)) {
                    if ("port".equals(key)) {
                        DB_CONFIG.put(key, Integer.parseInt(db.get(key).trim()));
                    } else {
                        DB_CONFIG.put(key, db.get(key));
                    }
                }
            }
            if (db.containsKey("web")) RunConfig.DB_WEB = db.get("web");
            if (db.containsKey("environment")) RunConfig.DB_ENVIRONMENT = db.get("environment");
        }
    }

    private static String envRaw(String name) {
        String value = System.getenv(ENV_PREFIX + name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    private static void loadEnv() {
        // RunConfig items
        String[][] runItems = {
                {"ENV", "ENV"}, {"BASE_URL", "BASE_URL"}, {"SYSTEM", "SYSTEM"},
                {"DATA_SOURCE", "DATA_SOURCE"}, {"SHEET_NAME", "SHEET_NAME"},
                {"WEB", "DB_WEB"}, {"DB_ENVIRONMENT", "DB_ENVIRONMENT"}, {"TAGS", "TAGS"}
        };
        for (String[] item : runItems) {
            String raw = envRaw(item[0]);
            if (raw != null) {
                setField(RunConfig.class, item[1], raw);
                ENV_OVERRIDES.add(item[0]);
            }
        }
        String raw;
        if ((raw = envRaw("WRITE_BACK")) != null) { RunConfig.WRITE_BACK = toBool(raw); ENV_OVERRIDES.add("WRITE_BACK"); }
        if ((raw = envRaw("TIMEOUT")) != null) { RunConfig.TIMEOUT = Integer.parseInt(raw.trim()); ENV_OVERRIDES.add("TIMEOUT"); }
        if ((raw = envRaw("VERIFY")) != null) { RunConfig.VERIFY = toBool(raw); ENV_OVERRIDES.add("VERIFY"); }
        if ((raw = envRaw("SMOKE")) != null) { RunConfig.SMOKE_ONLY = toBool(raw); ENV_OVERRIDES.add("SMOKE"); }
        if ((raw = envRaw("REPORT_TYPE")) != null) { RunConfig.REPORT_TYPE = raw.trim().toLowerCase(); ENV_OVERRIDES.add("REPORT_TYPE"); }

        // LogConfig
        if ((raw = envRaw("LOG_LEVEL")) != null) LogConfig.LEVEL = raw.trim().toUpperCase();
        if ((raw = envRaw("LOG_CONSOLE_LEVEL")) != null) LogConfig.CONSOLE_LEVEL = raw.trim().toUpperCase();
        if ((raw = envRaw("LOG_ROOT_NAME")) != null) LogConfig.ROOT_NAME = raw;

        // ReportConfig
        if ((raw = envRaw("REPORT_TITLE")) != null) ReportConfig.TITLE = raw;
        if ((raw = envRaw("REPORT_PROJECT")) != null) ReportConfig.PROJECT = raw;
        if ((raw = envRaw("REPORT_HTML_NAME")) != null) ReportConfig.HTML_NAME = raw;
        if ((raw = envRaw("ALLURE_MAX_ATTACH_LEN")) != null) ReportConfig.MAX_ATTACH_LEN = Integer.parseInt(raw.trim());

        // AuthConfig
        if ((raw = envRaw("OAUTH2_REFRESH_AHEAD")) != null) AuthConfig.REFRESH_AHEAD = Integer.parseInt(raw.trim());
        if ((raw = envRaw("OAUTH2_TOKEN_TIMEOUT")) != null) AuthConfig.TOKEN_TIMEOUT = Integer.parseInt(raw.trim());
        if ((raw = envRaw("OAUTH2_TOKEN_VERIFY")) != null) AuthConfig.TOKEN_VERIFY = toBool(raw);

        // DB config
        Map<String, String> dbItems = new LinkedHashMap<>();
        dbItems.put("DB_HOST", "host");
        dbItems.put("DB_PORT", "port");
        dbItems.put("DB_USER", "user");
        dbItems.put("DB_PASSWORD", "password");
        dbItems.put("DB_NAME", "database");
        dbItems.put("DB_CHARSET", "charset");
        for (Map.Entry<String, String> e : dbItems.entrySet()) {
            String v = envRaw(e.getKey());
            if (v != null) {
                if ("port".equals(e.getValue())) {
                    DB_CONFIG.put(e.getValue(), Integer.parseInt(v.trim()));
                } else {
                    DB_CONFIG.put(e.getValue(), v);
                }
            }
        }

        RunConfig.REPORT_TYPE = (RunConfig.REPORT_TYPE == null ? "allure" : RunConfig.REPORT_TYPE).trim().toLowerCase();
    }

    private static void setField(Class<?> clazz, String name, String value) {
        try {
            java.lang.reflect.Field f = clazz.getField(name);
            f.set(null, value);
        } catch (Exception e) {
            // ignore
        }
    }

    // --------------------------------------------------------------------------
    // 兼容旧接口
    // --------------------------------------------------------------------------
    public static String getLogPath() { return LOG_DIR; }
    public static String getReportPath() { return REPORT_DIR; }
    public static String getConfPath() { return CONFIG_DIR; }
    public static String getDataPath() { return DATA_DIR; }

    // --------------------------------------------------------------------------
    // 工具方法
    // --------------------------------------------------------------------------
    private static String envOr(String envKey, String defaultValue) {
        String v = System.getenv(envKey);
        return (v != null && !v.isEmpty()) ? v : defaultValue;
    }

    private static String join(String base, String... parts) {
        StringBuilder sb = new StringBuilder(base);
        for (String p : parts) {
            sb.append(File.separator).append(p);
        }
        return sb.toString();
    }

    private static void mkdirs(String... dirs) {
        for (String d : dirs) {
            File f = new File(d);
            if (!f.exists()) {
                f.mkdirs();
            }
        }
    }

    private Settings() {
    }
}
