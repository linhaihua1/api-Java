package com.amsapi.common;

/**
 * 框架全局常量集中定义。
 *
 * 设计原则：
 *   1. 所有「魔法值」（魔数、魔串）必须在此声明，禁止散落字面量
 *   2. 业务可配置项走 Settings / SystemProfile，此处只放协议级常量
 *   3. 常量命名全大写下划线，语义清晰
 */
public final class Constants {

    private Constants() {
    }

    // ==================================================================
    // 编码
    // ==================================================================
    public static final String ENC_UTF8 = "UTF-8";
    public static final String ENC_ISO_8859_1 = "ISO-8859-1";

    // ==================================================================
    // HTTP 协议
    // ==================================================================
    public static final String CT_JSON = "application/json";
    public static final String CT_FORM = "application/x-www-form-urlencoded";
    public static final String CT_MULTIPART = "multipart/form-data";
    public static final String CT_XML = "application/xml";
    public static final String CT_TEXT = "text/plain";

    public static final String HDR_CONTENT_TYPE = "Content-Type";
    public static final String HDR_SET_COOKIE = "Set-Cookie";

    // HTTP 方法
    public static final String M_GET = "get";
    public static final String M_POST = "post";
    public static final String M_PUT = "put";
    public static final String M_DELETE = "delete";
    public static final String M_PATCH = "patch";
    public static final String M_HEAD = "head";
    public static final String M_OPTIONS = "options";

    // 参数挂载位置
    public static final String LOC_PARAMS = "params";
    public static final String LOC_JSON = "json";
    public static final String LOC_DATA = "data";
    public static final String LOC_FILES = "files";

    // 默认 HTTP 成功状态码
    public static final int[] DEFAULT_HTTP_OK = {200, 201, 202, 204};

    // ==================================================================
    // 时间 / 缓冲
    // ==================================================================
    public static final int MS_PER_SECOND = 1000;
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    public static final int DEFAULT_TIMEOUT_SEC = 10;
    public static final int DEFAULT_NONCE_LEN = 16;
    public static final int DEFAULT_DB_PORT = 3306;
    public static final int AES_BLOCK_SIZE = 16;

    // ==================================================================
    // 默认值
    // ==================================================================
    public static final String DEFAULT_ENV = "test";
    public static final String DEFAULT_SYSTEM = "default";
    public static final String DEFAULT_REPORT_TYPE = "allure";
    public static final String DEFAULT_RESPONSE_FORMAT = "auto";
    public static final String DEFAULT_TAGS = "smoke";
    public static final String DEFAULT_CONTENT_TYPE = CT_JSON;
    public static final String DEFAULT_NONCE_CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789";
    public static final String DEFAULT_TS_UNIT = "s";

    // 响应判定风格
    public static final String STYLE_STATUS = "status";
    public static final String STYLE_CODE = "code";
    public static final String STYLE_SUCCESS = "success";
    public static final String STYLE_ERRNO = "errno";
    public static final String STYLE_RETCODE = "retcode";
    public static final String STYLE_RAW = "raw";

    // 数据源
    public static final String DS_EXCEL = "excel";
    public static final String DS_YAML = "yaml";
    public static final String DS_MYSQL = "mysql";
    public static final String DS_ALL = "all";

    // 报告类型
    public static final String[] VALID_REPORT_TYPES = {"allure", "html", "both", "none"};

    // ==================================================================
    // XML 约定
    // ==================================================================
    public static final String XML_ATTR_PREFIX = "@";
    public static final String XML_TEXT_KEY = "#text";
    public static final String XML_DEFAULT_ROOT = "root";
    public static final String XML_DEFAULT_ENC = "utf-8";

    // ==================================================================
    // 日志 / 报告
    // ==================================================================
    public static final String DEFAULT_LOG_ROOT = "amsapi";
    public static final String DEFAULT_LOG_LEVEL = "DEBUG";
    public static final int DEFAULT_MAX_ATTACH_LEN = 20000;

    // ==================================================================
    // 认证
    // ==================================================================
    public static final int DEFAULT_TOKEN_TIMEOUT = 10;
    public static final int DEFAULT_REFRESH_AHEAD = 60;

    // ==================================================================
    // 正则
    // ==================================================================
    public static final String PATTERN_VAR = "\\$\\{(.*?)\\}";

    // ==================================================================
    // 用例字段名（避免散落硬编码）
    // ==================================================================
    public static final String FIELD_ID = "id";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_METHOD = "method";
    public static final String FIELD_URL = "url";
    public static final String FIELD_HEADERS = "headers";
    public static final String FIELD_COOKIES = "cookies";
    public static final String FIELD_REQUEST_BODY = "request_body";
    public static final String FIELD_CONTENT_TYPE = "content_type";
    public static final String FIELD_REQUEST_TYPE = "request_type";
    public static final String FIELD_EXPECTS = "expects";
    public static final String FIELD_EXPECTED_CODE = "expected_code";
    public static final String FIELD_RELATION = "relation";
    public static final String FIELD_EXTRACT = "extract";
    public static final String FIELD_DEPENDS = "depends";
    public static final String FIELD_SETUP = "setup";
    public static final String FIELD_TEARDOWN = "teardown";
    public static final String FIELD_TAGS = "tags";
    public static final String FIELD_HOST = "host";
    public static final String FIELD_CHECK_BUSINESS = "check_business";
    public static final String FIELD_SOURCE = "__source__";

    // 响应结构字段
    public static final String RES_CODE = "code";
    public static final String RES_BODY = "body";
    public static final String RES_HEADERS = "headers";
    public static final String RES_COOKIES = "cookies";
    public static final String RES_ELAPSED = "elapsed";
    public static final String RES_URL = "url";
    public static final String RES_METHOD = "method";

    // 状态
    public static final String STATUS_PASS = "pass";
    public static final String STATUS_FAIL = "fail";
    public static final String CASE_STATUS_PREFIX = "__case_status__:";
}
