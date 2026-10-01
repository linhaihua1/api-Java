package com.amsapi.common.auth;

import java.util.Map;

/** 无需鉴权。 */
public class NoAuth extends AuthStrategy {
    @Override
    public String name() {
        return "none";
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, String> cookies, Map<String, Object> params) {
        // 不做任何事
    }
}
