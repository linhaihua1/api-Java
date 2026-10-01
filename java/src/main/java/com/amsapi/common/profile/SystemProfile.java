package com.amsapi.common.profile;

import com.amsapi.common.auth.AuthFactory;
import com.amsapi.common.auth.AuthStrategy;
import com.amsapi.common.base.Base;
import com.amsapi.common.exceptions.ConfigError;
import com.amsapi.common.hooks.Hooks;
import com.amsapi.common.response.ResponseSpec;
import com.amsapi.config.IniParser;
import com.amsapi.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统适配档案层。
 * 对应 Python 版 common/profile.py。
 *
 * 「一套框架适配多种 web 系统」的核心：把不同系统的差异（域名、认证、成功判定、
 * 报文格式编码、签名加密）从代码里抽出来放进一份档案。
 */
public class SystemProfile {
    private static final Logger logger = LoggerFactory.getLogger(SystemProfile.class);

    private static final Map<String, SystemProfile> CACHE = new ConcurrentHashMap<>();

    private final String name;
    private final String title;
    private final String description;
    private final Map<String, String> hosts = new LinkedHashMap<>();
    private final String baseUrl;
    private final String timeout;
    private final String verify;
    private final String encoding;
    private final String contentType;
    private final String responseFormat;
    public final Map<String, String> headers;
    public final Map<String, String> cookies;
    public final ResponseSpec response;
    public final AuthStrategy auth;
    public final Hooks.HookRegistry hooks;

    @SuppressWarnings("unchecked")
    public SystemProfile(String name, Map<String, Map<String, String>> data) {
        this.name = name;

        Map<String, String> systemSec = lower(data.get("system"));
        Map<String, String> envSec = lower(data.get("env"));

        this.title = systemSec.getOrDefault("name", name);
        this.description = systemSec.getOrDefault("description", "");

        // hosts
        Map<String, String> hostsData = data.get("hosts");
        if (hostsData != null) {
            for (Map.Entry<String, String> e : hostsData.entrySet()) {
                String alias = e.getKey().trim().toLowerCase();
                String override = envValue("host_" + alias);
                String text = (override != null ? override : String.valueOf(e.getValue() == null ? "" : e.getValue())).trim();
                if (text.endsWith("/")) text = text.substring(0, text.length() - 1);
                if (!text.isEmpty()) hosts.put(alias, text);
            }
        }

        this.baseUrl = (envSec.getOrDefault("base_url", "")).trim();
        if (this.baseUrl.endsWith("/")) this.baseUrl.substring(0, this.baseUrl.length() - 1);
        this.timeout = envSec.get("timeout");
        this.verify = envSec.get("verify");
        this.encoding = (envSec.getOrDefault("encoding", "")).trim().isEmpty() ? null : envSec.get("encoding").trim();
        this.contentType = (envSec.getOrDefault("content_type", "")).trim().isEmpty() ? null : envSec.get("content_type").trim();
        this.responseFormat = (envSec.getOrDefault("response_format", "auto")).trim().toLowerCase();

        // headers (保留大小写)
        this.headers = new LinkedHashMap<>();
        Map<String, String> headersData = data.get("headers");
        if (headersData != null) {
            for (Map.Entry<String, String> e : headersData.entrySet()) {
                this.headers.put(e.getKey().trim(), e.getValue());
            }
        }

        // cookies (保留大小写)
        this.cookies = new LinkedHashMap<>();
        Map<String, String> cookiesData = data.get("cookies");
        if (cookiesData != null) {
            for (Map.Entry<String, String> e : cookiesData.entrySet()) {
                this.cookies.put(e.getKey().trim(), e.getValue());
            }
        }

        // response
        Map<String, String> respSec = lower(data.get("response"));
        this.response = new ResponseSpec(
                respSec.get("style"),
                respSec.get("success_path"),
                respSec.get("success_values"),
                respSec.get("message_path"),
                respSec.get("data_path"),
                respSec.get("http_ok")
        );

        // auth - 支持 AMSAPI_AUTH_* 覆盖
        Map<String, String> authSec = new LinkedHashMap<>(lower(data.get("auth")));
        for (String key : new ArrayList<>(authSec.keySet())) {
            String override = envValue("auth_" + key);
            if (override != null) authSec.put(key, override);
        }
        this.auth = AuthFactory.build(authSec);

        // hooks - 支持环境变量覆盖
        Map<String, String> hooksSec = lower(data.get("hooks"));
        for (String key : new String[]{"before_request", "after_response"}) {
            String override = envValue("hooks_" + key);
            if (override != null && !override.isEmpty()) hooksSec.put(key, override);
        }
        this.hooks = new Hooks.HookRegistry(hooksSec.get("before_request"), hooksSec.get("after_response"));
    }

    private static Map<String, String> lower(Map<String, String> data) {
        Map<String, String> result = new LinkedHashMap<>();
        if (data == null) return result;
        for (Map.Entry<String, String> e : data.entrySet()) {
            result.put(e.getKey().trim().toLowerCase(), e.getValue());
        }
        return result;
    }

    private static String envValue(String field) {
        String value = System.getenv(Settings.ENV_PREFIX + field.toUpperCase());
        if (value == null || value.trim().isEmpty()) return null;
        return value;
    }

    // ------------------------------------------------------------------
    // 运行期参数
    // ------------------------------------------------------------------
    public String getBaseUrl() { return baseUrl; }

    public String getEncoding() {
        String v = envValue("encoding");
        return v != null ? v : encoding;
    }

    public String getContentType() {
        String v = envValue("content_type");
        return v != null ? v : contentType;
    }

    public String getResponseFormat() {
        String v = envValue("response_format");
        return (v != null ? v : (responseFormat != null ? responseFormat : "auto")).toLowerCase();
    }

    public int getTimeout() {
        if (Settings.ENV_OVERRIDES.contains("TIMEOUT")) return Settings.RunConfig.TIMEOUT;
        if (timeout != null && !timeout.isEmpty()) return Integer.parseInt(timeout.trim());
        return Settings.RunConfig.TIMEOUT;
    }

    public boolean getVerify() {
        if (Settings.ENV_OVERRIDES.contains("VERIFY")) return Settings.RunConfig.VERIFY;
        if (verify == null || verify.isEmpty()) return Settings.RunConfig.VERIFY;
        return Base.toBool(verify, Settings.RunConfig.VERIFY);
    }

    public String getEffectiveBaseUrl() {
        String url = (Settings.RunConfig.BASE_URL != null && !Settings.RunConfig.BASE_URL.isEmpty())
                ? Settings.RunConfig.BASE_URL : (baseUrl != null ? baseUrl : "");
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    // ------------------------------------------------------------------
    // 域名解析
    // ------------------------------------------------------------------
    public String resolveUrl(String path, String host, String defaultBase) {
        if (path == null) return "";
        path = path.trim();
        if (path.isEmpty()) return path;
        if (path.toLowerCase().startsWith("http://") || path.toLowerCase().startsWith("https://")) {
            return path;
        }
        // 内联别名
        if (path.contains(":")) {
            String prefix = path.substring(0, path.indexOf(":")).trim().toLowerCase();
            if (hosts.containsKey(prefix)) {
                String rest = path.substring(path.indexOf(":") + 1);
                return hosts.get(prefix) + "/" + (rest.startsWith("/") ? rest.substring(1) : rest);
            }
        }
        String baseUrl = null;
        if (host != null && !host.isEmpty()) {
            baseUrl = hosts.get(host.trim().toLowerCase());
            if (baseUrl == null) {
                logger.warn("档案 {} 中未声明域名别名 '{}'，回退主域名", name, host);
            }
        }
        baseUrl = baseUrl != null ? baseUrl : (defaultBase != null ? defaultBase : getEffectiveBaseUrl());
        if (baseUrl == null || baseUrl.isEmpty()) return path;
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String p = path.startsWith("/") ? path.substring(1) : path;
        return baseUrl + "/" + p;
    }

    // ------------------------------------------------------------------
    // 展示
    // ------------------------------------------------------------------
    public Map<String, String> describe() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("系统档案", name);
        info.put("系统名称", title);
        info.put("主域名", getEffectiveBaseUrl().isEmpty() ? "(用例自带完整地址)" : getEffectiveBaseUrl());
        info.put("响应格式", getResponseFormat());
        info.put("响应编码", getEncoding() == null ? "auto" : getEncoding());
        info.put("超时(秒)", String.valueOf(getTimeout()));
        if (!hosts.isEmpty()) info.put("域名别名", String.join(", ", new TreeSet<>(hosts.keySet())));
        info.putAll(response.describe());
        info.putAll(auth.describe());
        info.putAll(hooks.describe());
        return info;
    }

    public String getName() { return name; }

    // ------------------------------------------------------------------
    // 加载
    // ------------------------------------------------------------------
    public static List<String> availableSystems() {
        File dir = new File(Settings.SYSTEMS_DIR);
        if (!dir.isDirectory()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".ini"));
        if (files != null) {
            for (File f : files) {
                result.add(f.getName().substring(0, f.getName().length() - 4));
            }
        }
        Collections.sort(result);
        return result;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static SystemProfile loadProfile() {
        return loadProfile(null, false);
    }

    public static SystemProfile loadProfile(String system) {
        return loadProfile(system, false);
    }

    public static SystemProfile loadProfile(String system, boolean force) {
        String name = (system != null && !system.isEmpty()) ? system.trim()
                : (Settings.RunConfig.SYSTEM != null && !Settings.RunConfig.SYSTEM.isEmpty()
                ? Settings.RunConfig.SYSTEM.trim() : "default");
        if (name.isEmpty()) name = "default";

        if (!force && CACHE.containsKey(name)) {
            return CACHE.get(name);
        }

        File path = new File(Settings.SYSTEMS_DIR, name + ".ini");
        Map<String, Map<String, String>> data;
        if (path.exists()) {
            data = IniParser.read(path.toPath());
        } else if ("default".equals(name)) {
            data = new LinkedHashMap<>();
        } else {
            throw new ConfigError("未找到系统适配档案 '" + name + "。\n" +
                    "  期望文件：" + path.getAbsolutePath() + "\n" +
                    "  可用档案：" + String.join(", ", availableSystems()) + "\n" +
                    "可用 AMSAPI_SYSTEM=<名称> 或 config/conf.ini 的 [env] system 指定。");
        }

        SystemProfile profile = new SystemProfile(name, data);
        CACHE.put(name, profile);
        logger.info("【适配】加载系统档案：{}（{}）", profile.name, profile.title);
        return profile;
    }
}
