package com.amsapi.common.hooks;

import com.amsapi.common.Constants;
import com.amsapi.common.base.Base;
import com.amsapi.common.variable.GlobalVar;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;

/**
 * 请求/响应钩子层与签名加密工具。
 * 对应 Python 版 common/hooks.py。
 *
 * 两个挂载阶段：
 *   before_request  请求发出前 —— 注入时间戳/随机数、签名、加密报文字段
 *   after_response  响应收到后 —— 解密响应字段、校验响应签名
 */
public final class Hooks {
    private static final Logger logger = LoggerFactory.getLogger(Hooks.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String STAGE_BEFORE = "before_request";
    public static final String STAGE_AFTER = "after_response";

    private Hooks() {
    }

    // ======================================================================
    // 一、钩子注册表
    // ======================================================================

    /**
     * 按顶层分隔符切分字符串，忽略括号内部的分隔符。
     * 钩子声明允许带 JSON 参数，其内部有逗号，不能被朴素切分劈开。
     */
    public static List<String> splitTopLevel(String text, char sep) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth = Math.max(0, depth - 1);
            if (c == sep && depth == 0) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString().trim());
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) result.add(p);
        }
        return result;
    }

    /**
     * 把 "包.模块:函数名" 解析成可调用对象。
     * 在 Java 中通过反射调用静态方法。
     */
    public static Object resolveCallable(String text) throws Exception {
        String modulePath, funcName;
        if (text.contains(":")) {
            String[] parts = text.split(":", 2);
            modulePath = parts[0];
            funcName = parts[1];
        } else {
            int idx = text.lastIndexOf(".");
            modulePath = text.substring(0, idx);
            funcName = text.substring(idx + 1);
        }
        if (modulePath.isEmpty() || funcName.isEmpty()) {
            throw new IllegalArgumentException("钩子声明格式错误，应为 '包.模块:函数名'：" + text);
        }
        Class<?> clazz = Class.forName(modulePath);
        // 查找无参静态方法（工厂方法）
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(funcName) && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                return m;
            }
        }
        throw new NoSuchMethodException("未找到静态方法: " + modulePath + "." + funcName);
    }

    /**
     * 把钩子声明解析成可调用对象（Function<Map,Map>）。
     */
    @SuppressWarnings("unchecked")
    public static Function<Map<String, Object>, Map<String, Object>> loadHook(Object spec) {
        if (spec == null || "".equals(spec)) return null;
        if (spec instanceof Function) return (Function<Map<String, Object>, Map<String, Object>>) spec;

        String text = String.valueOf(spec).trim();
        if (text.isEmpty()) return null;

        // 带参数的工厂写法
        if (text.endsWith(")") && text.contains("(")) {
            try {
                int parenIdx = text.indexOf("(");
                String head = text.substring(0, parenIdx).trim();
                Method factory = (Method) resolveCallable(head);
                String argsText = text.substring(parenIdx + 1, text.lastIndexOf(")")).trim();
                Object[] args;
                if (argsText.isEmpty()) {
                    args = new Object[0];
                } else {
                    Map<String, Object> kwargs = MAPPER.readValue(argsText, Map.class);
                    args = new Object[]{kwargs};
                }
                Object result = factory.invoke(null, args);
                if (result instanceof Function) {
                    return (Function<Map<String, Object>, Map<String, Object>>) result;
                }
            } catch (Exception e) {
                throw new RuntimeException("解析钩子失败: " + text + " - " + e.getMessage(), e);
            }
        }

        // 直接引用函数
        try {
            Method method = (Method) resolveCallable(text);
            return ctx -> {
                try {
                    Object result = method.invoke(null, ctx);
                    return result instanceof Map ? (Map<String, Object>) result : ctx;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("解析钩子失败: " + text, e);
        }
    }

    /**
     * 钩子注册表。按顺序执行，前一个钩子的产出传给下一个。
     */
    public static class HookRegistry {
        private final List<Function<Map<String, Object>, Map<String, Object>>> before = new ArrayList<>();
        private final List<Function<Map<String, Object>, Map<String, Object>>> after = new ArrayList<>();

        public HookRegistry(Object beforeSpec, Object afterSpec) {
            for (Object item : toList(beforeSpec)) {
                Function<Map<String, Object>, Map<String, Object>> hook = loadHook(item);
                if (hook != null) before.add(hook);
            }
            for (Object item : toList(afterSpec)) {
                Function<Map<String, Object>, Map<String, Object>> hook = loadHook(item);
                if (hook != null) after.add(hook);
            }
        }

        @SuppressWarnings("unchecked")
        private List<Object> toList(Object value) {
            if (value == null || "".equals(value)) return Collections.emptyList();
            if (value instanceof Collection) return new ArrayList<>((Collection<Object>) value);
            return (List) splitTopLevel(String.valueOf(value), ',');
        }

        public Map<String, Object> runBefore(Map<String, Object> ctx) {
            for (Function<Map<String, Object>, Map<String, Object>> hook : before) {
                ctx = hook.apply(ctx);
                if (ctx == null) ctx = new LinkedHashMap<>();
            }
            return ctx;
        }

        public Map<String, Object> runAfter(Map<String, Object> ctx) {
            for (Function<Map<String, Object>, Map<String, Object>> hook : after) {
                ctx = hook.apply(ctx);
                if (ctx == null) ctx = new LinkedHashMap<>();
            }
            return ctx;
        }

        public boolean hasBefore() { return !before.isEmpty(); }
        public boolean hasAfter() { return !after.isEmpty(); }

        public Map<String, String> describe() {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("请求前钩子", before.isEmpty() ? "(无)" : String.valueOf(before.size()) + " 个");
            info.put("响应后钩子", after.isEmpty() ? "(无)" : String.valueOf(after.size()) + " 个");
            return info;
        }
    }

    // ======================================================================
    // 二、摘要与签名工具
    // ======================================================================

    public static String digest(String text, String alg, String key) {
        try {
            byte[] raw = text.getBytes(StandardCharsets.UTF_8);
            String name = (alg == null ? "md5" : alg).trim().toLowerCase();
            if (name.startsWith("hmac-")) {
                String real = name.split("-", 2)[1];
                byte[] secret = (key == null ? "" : key).getBytes(StandardCharsets.UTF_8);
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("Hmac" + real.toUpperCase());
                mac.init(new SecretKeySpec(secret, "Hmac" + real.toUpperCase()));
                return bytesToHex(mac.doFinal(raw));
            }
            MessageDigest md = MessageDigest.getInstance(name);
            return bytesToHex(md.digest(raw));
        } catch (Exception e) {
            throw new RuntimeException("摘要计算失败: " + e.getMessage(), e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String signSorted(Map<String, Object> params, String salt, String alg,
                                    boolean upper, String[] exclude, String join, String kvSep, boolean skipEmpty) {
        Set<String> excludeSet = new HashSet<>();
        if (exclude != null) for (String e : exclude) excludeSet.add(e.trim());

        List<String> items = new ArrayList<>();
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (excludeSet.contains(key)) continue;
            Object value = params.get(key);
            if (skipEmpty && (value == null || "".equals(value))) continue;
            if (value instanceof Map || value instanceof List) {
                try {
                    value = MAPPER.writeValueAsString(value);
                } catch (Exception ignored) {}
            }
            items.add(key + kvSep + value);
        }
        String text = String.join(join, items) + (salt == null ? "" : salt);
        String result = digest(text, alg, null);
        return upper ? result.toUpperCase() : result;
    }

    // ======================================================================
    // 三、内置钩子工厂
    // ======================================================================

    public static Function<Map<String, Object>, Map<String, Object>> makeTimestampNonceHook(Map<String, Object> kwargs) {
        String tsField = kwargs.containsKey("fields") ? ((List<String>) kwargs.get("fields")).get(0) : "timestamp";
        String nonceField = kwargs.containsKey("fields") ? ((List<String>) kwargs.get("fields")).get(1) : "nonce";
        String tsUnit = kwargs.getOrDefault("ts_unit", Constants.DEFAULT_TS_UNIT).toString();
        int nonceLen = kwargs.containsKey("nonce_len") ? ((Number) kwargs.get("nonce_len")).intValue() : Constants.DEFAULT_NONCE_LEN;

        return ctx -> {
            Object payload = ctx.get("payload");
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) payload;
                long value = System.currentTimeMillis() / ("ms".equals(tsUnit) ? 1 : Constants.MS_PER_SECOND);
                if (kwargs.containsKey("ts_digits")) {
                    int digits = ((Number) kwargs.get("ts_digits")).intValue();
                    String s = String.valueOf(value);
                    value = Long.parseLong(s.substring(Math.max(0, s.length() - digits)));
                }
                p.put(tsField, value);
                StringBuilder sb = new StringBuilder();
                String chars = Constants.DEFAULT_NONCE_CHARSET;
                Random rnd = new Random();
                for (int i = 0; i < nonceLen; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
                p.put(nonceField, sb.toString());
            }
            return ctx;
        };
    }

    public static Function<Map<String, Object>, Map<String, Object>> makeSignHook(Map<String, Object> kwargs) {
        String field = kwargs.getOrDefault("field", "sign").toString();
        String salt = kwargs.getOrDefault("salt", "").toString();
        String alg = kwargs.getOrDefault("alg", "md5").toString();
        boolean upper = kwargs.containsKey("upper") && (Boolean) kwargs.get("upper");
        String[] exclude = kwargs.containsKey("exclude")
                ? ((List<String>) kwargs.get("exclude")).toArray(new String[0]) : new String[]{"sign"};
        String target = kwargs.getOrDefault("target", "payload").toString();
        String header = kwargs.getOrDefault("header", "Sign").toString();

        return ctx -> {
            Object payload = ctx.get("payload");
            if (!(payload instanceof Map)) return ctx;
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) payload;
            String value = signSorted(p, salt, alg, upper, exclude, "&", "=", true);
            if ("header".equals(target)) {
                @SuppressWarnings("unchecked")
                Map<String, String> headers = (Map<String, String>) ctx.getOrDefault("headers", new LinkedHashMap<>());
                headers.put(header, value);
                ctx.put("headers", headers);
            } else {
                p.put(field, value);
            }
            return ctx;
        };
    }

    // ======================================================================
    // 四、AES 加解密（可选依赖，JDK 内置即可满足 ECB/CBC + PKCS5）
    // ======================================================================

    private static byte[] pkcs7Pad(byte[] raw, int block) {
        int pad = block - raw.length % block;
        byte[] result = Arrays.copyOf(raw, raw.length + pad);
        Arrays.fill(result, raw.length, result.length, (byte) pad);
        return result;
    }

    private static byte[] pkcs7Unpad(byte[] raw) {
        if (raw == null || raw.length == 0) return raw;
        int pad = raw[raw.length - 1];
        return Arrays.copyOf(raw, raw.length - pad);
    }

    public static String aesEncrypt(String plain, String key, String iv, String mode, boolean b64) throws Exception {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] raw = pkcs7Pad(plain.getBytes(StandardCharsets.UTF_8), Constants.AES_BLOCK_SIZE);
        Cipher cipher = Cipher.getInstance("AES/" + (mode == null ? "ECB" : mode.toUpperCase()) + "/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        if ("CBC".equalsIgnoreCase(mode)) {
            byte[] ivBytes = (iv == null ? "" : iv).getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        }
        byte[] result = cipher.doFinal(raw);
        return b64 ? Base64.getEncoder().encodeToString(result) : new String(result, StandardCharsets.ISO_8859_1);
    }

    public static String aesDecrypt(String cipherText, String key, String iv, String mode, boolean b64) throws Exception {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] raw = b64 ? Base64.getDecoder().decode(cipherText) : cipherText.getBytes(StandardCharsets.ISO_8859_1);
        Cipher cipher = Cipher.getInstance("AES/" + (mode == null ? "ECB" : mode.toUpperCase()) + "/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        if ("CBC".equalsIgnoreCase(mode)) {
            byte[] ivBytes = (iv == null ? "" : iv).getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
        } else {
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
        }
        byte[] result = pkcs7Unpad(cipher.doFinal(raw));
        return new String(result, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static Function<Map<String, Object>, Map<String, Object>> makeAesHook(Map<String, Object> kwargs) {
        String key = kwargs.get("key").toString();
        List<String> fields = kwargs.containsKey("fields") ? (List<String>) kwargs.get("fields") : Arrays.asList("data");
        String iv = kwargs.getOrDefault("iv", null) != null ? kwargs.get("iv").toString() : null;
        String mode = kwargs.getOrDefault("mode", "ECB").toString();
        String algField = kwargs.getOrDefault("alg_field", null) != null ? kwargs.get("alg_field").toString() : null;

        return ctx -> {
            Object payload = ctx.get("payload");
            if (!(payload instanceof Map)) return ctx;
            Map<String, Object> p = (Map<String, Object>) payload;
            for (String name : fields) {
                if (p.containsKey(name)) {
                    Object plain = p.get(name);
                    String plainStr;
                    if (!(plain instanceof String)) {
                        try { plainStr = MAPPER.writeValueAsString(plain); }
                        catch (Exception e) { plainStr = String.valueOf(plain); }
                    } else {
                        plainStr = (String) plain;
                    }
                    try {
                        p.put(name, aesEncrypt(plainStr, key, iv, mode, true));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            if (algField != null) p.put(algField, "AES/" + mode.toUpperCase());
            return ctx;
        };
    }

    @SuppressWarnings("unchecked")
    public static Function<Map<String, Object>, Map<String, Object>> makeDecryptResponseHook(Map<String, Object> kwargs) {
        String key = kwargs.get("key").toString();
        List<String> fields = kwargs.containsKey("fields") ? (List<String>) kwargs.get("fields") : Arrays.asList("data");
        String iv = kwargs.getOrDefault("iv", null) != null ? kwargs.get("iv").toString() : null;
        String mode = kwargs.getOrDefault("mode", "ECB").toString();

        return ctx -> {
            Object responseObj = ctx.get("response");
            if (!(responseObj instanceof Map)) return ctx;
            Map<String, Object> response = (Map<String, Object>) responseObj;
            Object bodyObj = response.get("body");
            if (!(bodyObj instanceof Map)) return ctx;
            Map<String, Object> body = (Map<String, Object>) bodyObj;
            for (String name : fields) {
                Object value = body.get(name);
                if (value instanceof String && !((String) value).isEmpty()) {
                    try {
                        body.put(name, aesDecrypt((String) value, key, iv, mode, true));
                    } catch (Exception e) {
                        logger.warn("响应字段 {} 解密失败：{}", name, e.getMessage());
                    }
                }
            }
            return ctx;
        };
    }

    @SuppressWarnings("unchecked")
    public static Function<Map<String, Object>, Map<String, Object>> makeTokenResponseHook(Map<String, Object> kwargs) {
        String tokenPath = kwargs.getOrDefault("token_path", "body.data.token").toString();
        String varName = kwargs.getOrDefault("var_name", "token").toString();

        return ctx -> {
            Object responseObj = ctx.get("response");
            if (!(responseObj instanceof Map)) return ctx;
            Map<String, Object> response = (Map<String, Object>) responseObj;
            Object value = Base.parseRelation(Arrays.asList(tokenPath.split("\\.")), response);
            if (value != null) {
                GlobalVar.set(varName, value);
                logger.info("【认证】从响应提取 {} = {}", varName, value);
            }
            return ctx;
        };
    }
}
