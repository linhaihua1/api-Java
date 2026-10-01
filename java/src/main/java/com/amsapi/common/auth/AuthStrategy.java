package com.amsapi.common.auth;

import com.amsapi.common.base.Base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 认证策略基类。
 * 对应 Python 版 common/auth.py 的 AuthStrategy。
 *
 * 令牌值支持 ${var} 占位符，在每次请求前从全局变量池实时解析，
 * 因此「先调登录接口拿 ticket，后续接口自动带上」无需任何额外代码。
 */
public abstract class AuthStrategy {
    protected static final Logger logger = LoggerFactory.getLogger(AuthStrategy.class);

    public abstract String name();

    /**
     * 把认证信息附加到请求要素上（就地修改入参的 Map）。
     * @param headers 请求头
     * @param cookies cookie
     * @param params  query 参数
     */
    public abstract void apply(Map<String, String> headers, Map<String, String> cookies,
                               Map<String, Object> params);

    public Map<String, String> describe() {
        Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("认证方式", name());
        return info;
    }

    /**
     * 解析凭证里可能存在的 ${var} 占位符（值来自全局变量池）。
     */
    protected String resolveCredential(String value) {
        if (value != null && value.contains("${")) {
            String resolved = Base.replace(value);
            if (resolved.contains("${")) {
                logger.warn("认证凭证中的变量未解析：{}（请确认前序接口已提取该变量）", value);
            }
            return resolved;
        }
        return value;
    }
}
