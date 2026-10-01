package com.amsapi.api;

import java.util.Map;

/**
 * 登录接口对象。
 * 对应 Python 版 api/login_api.py。
 */
public class LoginApi extends BaseApi {

    public LoginApi() {
        super();
    }

    public Map<String, Object> login(String username, String password) throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        return request("post", "/v1/users/login", body, null, null, null, null);
    }
}
