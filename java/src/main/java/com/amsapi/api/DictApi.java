package com.amsapi.api;

import java.util.Map;

/**
 * 字典接口对象。
 * 对应 Python 版 api/dict_api.py。
 */
public class DictApi extends BaseApi {

    public DictApi() {
        super();
    }

    public Map<String, Object> getDict(String type) throws Exception {
        return request("get", "/v1/dict/" + type, null, null, null, null, null);
    }
}
