package com.amsapi.common.exceptions;

/**
 * 配置错误异常。
 * 对应 Python 版 common/exceptions.py 中的 ConfigError。
 */
public class ConfigError extends RuntimeException {
    public ConfigError(String message) {
        super(message);
    }

    public ConfigError(String message, Throwable cause) {
        super(message, cause);
    }
}
