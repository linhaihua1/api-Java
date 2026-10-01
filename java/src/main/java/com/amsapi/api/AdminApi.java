package com.amsapi.api;

import java.util.Map;

/**
 * 管理端接口对象。
 * 对应 Python 版 api/admin_api.py。
 */
public class AdminApi extends BaseApi {

    public AdminApi() {
        super();
    }

    public Map<String, Object> getUserList(Map<String, Object> params) throws Exception {
        return request("get", "/v1/admin/users", params, null, null, null, null);
    }
}
