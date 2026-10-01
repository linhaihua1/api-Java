package com.amsapi.common.base;

import com.amsapi.common.variable.GlobalVar;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基础解析层。
 * 对应 Python 版 common/base.py。
 *
 * 负责「参数化」的核心能力：
 *   1. find()         从数据中正则找出所有 ${var} 占位符名
 *   2. replace()      把 ${var} 替换成全局变量池中的真实值
 *   3. parseRelation() 按 'a.b.c' 路径从响应中取出目标值（关联提取）
 *   4. safeLoads()    安全地把字符串解析成对象（不执行任何代码）
 *   5. XML 互转        xmlToDict / dictToXml
 */
public final class Base {

    private static final Pattern PATTERN = Pattern.compile("\\$\\{(.*?)\\}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // XML 约定
    public static final String ATTR_PREFIX = "@";
    public static final String TEXT_KEY = "#text";

    private Base() {
    }

    // ======================================================================
    // 一、占位符查找与替换
    // ======================================================================

    /**
     * 从 data（Map / List / String）中找出所有 ${var} 占位符名称。
     * @return 占位符名列表；无匹配返回空列表
     */
    public static List<String> find(Object data) {
        String text = dumps(data);
        if (text == null) {
            return Collections.emptyList();
        }
        Matcher m = PATTERN.matcher(text);
        List<String> result = new ArrayList<>();
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    private static String dumps(Object data) {
        if (data == null) return null;
        if (data instanceof Map || data instanceof List) {
            try {
                return MAPPER.writeValueAsString(data);
            } catch (Exception e) {
                return String.valueOf(data);
            }
        }
        return String.valueOf(data);
    }

    /**
     * 将 ori_data 中的 ${var} 用 replace_data 或全局变量池中的值替换。
     * @return 替换后的字符串
     */
    @SuppressWarnings("unchecked")
    public static String replace(Object oriData, Map<String, Object> replaceData) {
        String oriStr = dumps(oriData);
        if (oriStr == null) return "";

        if (replaceData == null) {
            List<String> names = find(oriStr);
            replaceData = new LinkedHashMap<>();
            for (String name : names) {
                Object v = GlobalVar.get(name, "");
                replaceData.put(name, v == null ? "" : v);
            }
        }

        // 值统一转 String，避免非字符串报错
        Map<String, String> safeMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : replaceData.entrySet()) {
            safeMap.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }

        // 简单模板替换：${name} -> value
        Matcher m = PATTERN.matcher(oriStr);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val = safeMap.containsKey(key) ? safeMap.get(key) : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String replace(Object oriData) {
        return replace(oriData, null);
    }

    /**
     * replace 的对象版：直接返回 Java 对象（Map/List），解析失败返回字符串。
     */
    public static Object replaceObj(Object oriData, Map<String, Object> replaceData) {
        String text = replace(oriData, replaceData);
        try {
            return MAPPER.readValue(text, Object.class);
        } catch (Exception e) {
            return text;
        }
    }

    public static Object replaceObj(Object oriData) {
        return replaceObj(oriData, null);
    }

    // ======================================================================
    // 二、路径解析（关联提取）
    // ======================================================================

    /**
     * 按路径逐层获取嵌套对象的值。
     * @param var 路径列表，如 ["data", "ticket"]；元素可为 "0" 表示列表下标
     * @param resData 响应数据
     * @return 目标值；路径不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public static Object parseRelation(List<String> var, Object resData) {
        if (var == null || var.isEmpty()) {
            return resData;
        }
        Object cur = resData;
        for (String key : var) {
            if (cur == null) return null;
            try {
                if (cur instanceof List) {
                    cur = ((List<Object>) cur).get(Integer.parseInt(key));
                } else if (cur instanceof Map) {
                    cur = ((Map<String, Object>) cur).get(key);
                } else {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return cur;
    }

    /**
     * 解析关联表达式：`ticket=body.data.ticket` -> [name, value]
     * 支持列表下标：`first=body.data.rows.0.name`
     */
    public static Object[] parseExpression(String expr, Object resData) {
        if (!expr.contains("=")) {
            throw new IllegalArgumentException("关联表达式格式错误，应为 name=path 形式：" + expr);
        }
        int idx = expr.indexOf("=");
        String name = expr.substring(0, idx).trim();
        String path = expr.substring(idx + 1).trim();
        Object value = parseRelation(Arrays.asList(path.split("\\.")), resData);
        return new Object[]{name, value};
    }

    // ======================================================================
    // 三、安全解析（替代 eval）
    // ======================================================================

    /**
     * 安全地把字符串解析成 Java 对象（不执行任何代码）。
     * 优先按 JSON 解析，失败再尝试字面量解析（兼容单引号写法）。
     * @param text 待解析内容，可为 String / Map / List / 基本类型
     * @param defaultVal 解析失败时的返回值
     */
    public static Object safeLoads(Object text, Object defaultVal) {
        if (text == null) return defaultVal;
        if (text instanceof Map || text instanceof List || text instanceof Number
                || text instanceof Boolean) {
            return text;
        }
        String s = String.valueOf(text).trim();
        if (s.isEmpty()) return defaultVal;

        // 尝试 JSON
        try {
            return MAPPER.readValue(s, Object.class);
        } catch (Exception ignored) {
        }

        // 尝试单引号 JSON（Python 风格 dict）
        try {
            String normalized = s.replace("'", "\"");
            return MAPPER.readValue(normalized, Object.class);
        } catch (Exception ignored) {
        }

        return defaultVal;
    }

    // ======================================================================
    // 四、布尔 / 标签
    // ======================================================================

    public static boolean toBool(Object value, boolean defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Boolean) return (Boolean) value;
        return String.valueOf(value).trim().toLowerCase().matches("1|true|yes|on|y");
    }

    /**
     * 把标签字段统一解析为字符串列表。
     * 兼容 "smoke" / "smoke,core" / ["smoke","core"] 等写法。
     */
    @SuppressWarnings("unchecked")
    public static List<String> parseTags(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object item : (Collection<Object>) value) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) result.add(s);
            }
            return result;
        }
        String text = String.valueOf(value).replace("，", ",").replace("、", ",");
        List<String> result = new ArrayList<>();
        for (String item : text.split(",")) {
            String s = item.trim();
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    /**
     * 判断用例标签是否命中筛选条件（命中任意一个即算匹配）。
     */
    public static boolean matchTags(Object caseTags, Object wanted) {
        List<String> wantedList = parseTags(wanted);
        if (wantedList.isEmpty()) return true;
        Set<String> caseSet = new HashSet<>(parseTags(caseTags));
        for (String w : wantedList) {
            if (caseSet.contains(w)) return true;
        }
        return false;
    }

    // ======================================================================
    // 五、XML 解析与构造
    // ======================================================================

    private static final Pattern NUM_PATTERN = Pattern.compile("-?(0|[1-9]\\d*)");
    private static final Pattern FLOAT_PATTERN = Pattern.compile("-?(0|[1-9]\\d*)?\\.\\d+");

    private static boolean shouldConvert(String text) {
        return NUM_PATTERN.matcher(text).matches() || FLOAT_PATTERN.matcher(text).matches();
    }

    private static Object convert(String text) {
        text = text.trim();
        if (text.isEmpty() || !shouldConvert(text)) return text;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e2) {
                return text;
            }
        }
    }

    private static String tagName(String tag) {
        int idx = tag.lastIndexOf("}");
        return idx >= 0 ? tag.substring(idx + 1) : tag;
    }

    @SuppressWarnings("unchecked")
    private static Object elementToObj(Element element) {
        NodeList children = element.getChildNodes();
        Map<String, Object> attrs = new LinkedHashMap<>();
        NamedNodeMap attrMap = element.getAttributes();
        for (int i = 0; i < attrMap.getLength(); i++) {
            Node attr = attrMap.item(i);
            attrs.put(ATTR_PREFIX + tagName(attr.getNodeName()), attr.getNodeValue());
        }
        String text = (element.getTextContent() == null ? "" : element.getTextContent()).trim();

        // 找出真正的子元素（跳过文本节点）
        List<Element> childElements = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                childElements.add((Element) n);
            }
        }

        if (childElements.isEmpty()) {
            if (!attrs.isEmpty()) {
                if (!text.isEmpty()) {
                    attrs.put(TEXT_KEY, convert(text));
                }
                return attrs;
            }
            return convert(text);
        }

        Map<String, Object> result = new LinkedHashMap<>(attrs);
        for (Element child : childElements) {
            String name = tagName(child.getTagName());
            Object value = elementToObj(child);
            if (result.containsKey(name)) {
                Object existing = result.get(name);
                if (!(existing instanceof List)) {
                    List<Object> list = new ArrayList<>();
                    list.add(existing);
                    result.put(name, list);
                }
                ((List<Object>) result.get(name)).add(value);
            } else {
                result.put(name, value);
            }
        }
        if (!text.isEmpty()) {
            result.put(TEXT_KEY, convert(text));
        }
        return result;
    }

    /**
     * 把 XML 文本解析成嵌套字典。
     * 约定（与主流 XML 库一致）：
     *   子元素 -> 同名键；同名多次 -> list
     *   属性 -> @属性名
     *   文本 -> #text
     *   数字文本 -> 自动转 int / double
     *   命名空间 -> 自动剥离
     * @return Map；解析失败返回 null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> xmlToDict(Object text) {
        if (text == null) return null;
        if (!(text instanceof String || text instanceof byte[])) return null;

        byte[] bytes;
        if (text instanceof byte[]) {
            bytes = (byte[]) text;
        } else {
            bytes = ((String) text).getBytes(StandardCharsets.UTF_8);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bytes));
            Element root = doc.getDocumentElement();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(tagName(root.getTagName()), elementToObj(root));
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把字典构造成 XML 文本（xmlToDict 的逆操作）。
     */
    public static String dictToXml(Object data, String root, boolean declaration, String encoding) {
        if (data == null) return "";
        String rootName = root == null ? "root" : root;
        Object payload = data;

        if (data instanceof Map && ((Map<String, Object>) data).size() == 1 && "root".equals(root)) {
            Map.Entry<String, Object> entry = ((Map<String, Object>) data).entrySet().iterator().next();
            rootName = entry.getKey();
            payload = entry.getValue();
        }

        StringBuilder sb = new StringBuilder();
        if (declaration) {
            sb.append("<?xml version=\"1.0\" encoding=\"").append(encoding == null ? "utf-8" : encoding).append("\"?>");
        }
        fillXml(sb, rootName, payload);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void fillXml(StringBuilder sb, String tag, Object value) {
        sb.append("<").append(tag).append(">");
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            // 先处理属性
            Map<String, Object> regular = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (e.getKey().startsWith(ATTR_PREFIX)) {
                    sb.append(" ").append(e.getKey().substring(1)).append("=\"")
                            .append(e.getValue() == null ? "" : escapeXml(String.valueOf(e.getValue())))
                            .append("\"");
                } else {
                    regular.put(e.getKey(), e.getValue());
                }
            }
            for (Map.Entry<String, Object> e : regular.entrySet()) {
                if (TEXT_KEY.equals(e.getKey())) {
                    sb.append(e.getValue() == null ? "" : escapeXml(String.valueOf(e.getValue())));
                } else if (e.getValue() instanceof List) {
                    for (Object item : (List<Object>) e.getValue()) {
                        fillXml(sb, e.getKey(), item);
                    }
                } else {
                    fillXml(sb, e.getKey(), e.getValue());
                }
            }
        } else {
            sb.append(value == null ? "" : escapeXml(String.valueOf(value)));
        }
        sb.append("</").append(tag).append(">");
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    public static String dumpsJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
