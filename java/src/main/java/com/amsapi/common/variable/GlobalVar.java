package com.amsapi.common.variable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 全局变量池。
 * 对应 Python 版 common/variable.py 的 GlobalVar。
 *
 * 用途：接口之间的参数关联传递。
 *   用例 A 提取 ticket  -> GlobalVar.set("ticket", "xxx")
 *   用例 B 引用 ${ticket} -> GlobalVar.get("ticket")
 *
 * 支持点号路径读写，例如 set("user.id", 1) / get("user.id")。
 * 线程安全，支持并发执行。
 */
public class GlobalVar {
    private static final Map<String, Object> STORE = new HashMap<>();
    private static final ReentrantLock LOCK = new ReentrantLock();

    private GlobalVar() {
    }

    /**
     * 写入变量。key 支持 'a.b.c' 形式的层级路径（在 Python 版中存储为平铺 key）。
     */
    public static Object set(String key, Object value) {
        LOCK.lock();
        try {
            STORE.put(key, value);
            return value;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * 读取变量，不存在返回 default。
     * 先查精确 key，再尝试按点号路径逐层取值。
     */
    @SuppressWarnings("unchecked")
    public static Object get(String key, Object defaultValue) {
        LOCK.lock();
        try {
            if (STORE.containsKey(key)) {
                return STORE.get(key);
            }
            // 尝试按点号路径逐层取值
            Object cur = STORE;
            try {
                for (String part : key.split("\\.")) {
                    if (cur instanceof Map) {
                        cur = ((Map<String, Object>) cur).get(part);
                        if (cur == null) {
                            return defaultValue;
                        }
                    } else {
                        return defaultValue;
                    }
                }
                return cur != null ? cur : defaultValue;
            } catch (Exception e) {
                return defaultValue;
            }
        } finally {
            LOCK.unlock();
        }
    }

    public static Object get(String key) {
        return get(key, null);
    }

    /**
     * 批量写入。
     */
    public static void update(Map<String, ?> mapping) {
        if (mapping == null) {
            return;
        }
        LOCK.lock();
        try {
            STORE.putAll(mapping);
        } finally {
            LOCK.unlock();
        }
    }

    public static boolean has(String key) {
        return get(key, MISS) != MISS;
    }

    private static final Object MISS = new Object();

    public static Map<String, Object> all() {
        LOCK.lock();
        try {
            return new HashMap<>(STORE);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * 清空变量池。每个测试会话开始前调用，避免用例间脏数据。
     */
    public static void clear() {
        LOCK.lock();
        try {
            STORE.clear();
        } finally {
            LOCK.unlock();
        }
    }
}
