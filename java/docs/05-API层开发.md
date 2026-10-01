# 05 - API 层开发指南

## 一、BaseApi 核心能力

`BaseApi` 是所有接口对象的父类，提供 8 项统一能力。日常写接口用例时，大多数场景不需要直接使用 BaseApi，通过 YAML/Excel 数据驱动即可。但当需要编写复杂的 PO（Page Object）风格测试时，就需要继承 BaseApi。

## 二、创建自定义接口对象

### 2.1 最简示例

```java
package com.amsapi.api;

import java.util.Map;

public class UserApi extends BaseApi {

    public UserApi() {
        super();  // 使用默认配置（从系统档案加载）
    }

    public Map<String, Object> getUser(String userId) throws Exception {
        return request("get", "/v1/users/" + userId, null, null, null, null, null);
    }

    public Map<String, Object> createUser(String name, String email) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", name);
        body.put("email", email);
        return request("post", "/v1/users", body, null, null, null, null);
    }
}
```

### 2.2 request 方法签名

```java
public Map<String, Object> request(
    String method,                    // "get" / "post" / ...
    Object path,                      // "/v1/users/${id}" 或完整URL
    Object data,                      // 请求体（Map 或 JSON 字符串）
    Map<String, String> headers,      // 额外请求头（会合并档案中的公共头）
    Map<String, String> cookies,      // 额外 Cookie
    String host,                      // 域名别名（对应档案 [hosts]）
    Map<String, Object> extraParams   // 额外 query 参数
) throws Exception
```

### 2.3 自定义域名和请求头

```java
public class OrderApi extends BaseApi {

    public OrderApi() {
        super(
            "http://order-service.test.com",  // baseUrl（覆盖档案中的地址）
            Map.of("X-Service", "order"),     // 额外公共请求头
            30,                                // 超时秒数
            null,                              // 复用 HttpClient 会话
            null,                              // 自定义 SystemProfile
            null,                              // 自定义认证策略
            null                               // 额外公共 Cookie
        );
    }
}
```

## 三、断言方法

BaseApi 封装了常用断言，返回 `true` 或抛出 `AssertionError`：

```java
public void testLogin() throws Exception {
    LoginApi api = new LoginApi();
    api.login("admin", "123456");

    // HTTP 状态码
    api.checkStatus(200);

    // 业务成功判定（按系统档案规则）
    api.checkSuccess(null, "登录应成功");

    // 指定期望业务码
    api.checkSuccess(200, "状态码应为200");

    // 字段级断言
    api.assertEquals("body.data.username", "admin", "");
    api.assertNotEmpty("body.data.token", "");
    api.assertGt("elapsed", 0, "");
    api.assertType("body.data", "dict", "");
}
```

## 四、关联提取

```java
LoginApi api = new LoginApi();
api.login("admin", "123456");

// 提取响应中的 ticket 存入全局变量池
api.extract("ticket=body.data.ticket");

// 后续接口自动可用 ${ticket}
UserApi userApi = new UserApi();
userApi.getUser("${ticket}");
```

## 五、runCase — 用例生命周期

如果需要在代码中执行一个完整用例（类似数据驱动），使用 `runCase`：

```java
Map<String, Object> caseData = new LinkedHashMap<>();
caseData.put("url", "/v1/users/login");
caseData.put("method", "post");
caseData.put("request_body", Map.of("username", "admin", "password", "123"));
caseData.put("relation", "ticket=body.data.ticket");
caseData.put("expected_code", 200);

BaseApi api = new BaseApi();
api.runCase(caseData);  // 自动执行 setup → 请求 → 断言 → teardown
```

`runCase` 自动处理：
- `depends` 依赖检查
- `setup` 前置步骤
- 参数化（`${var}` 替换）
- 认证附加
- 请求发送
- `relation` 关联提取
- `expects` 断言执行
- 业务成功判定
- `teardown` 后置清理
- 用例状态记录

## 六、获取响应数据

```java
api.login("admin", "123");

// 获取完整响应
Map<String, Object> response = api.getResponse();

// 获取各部分
Object body = api.getBody();           // 响应体
Object code = api.getCode();           // HTTP 状态码
Object bizCode = api.getBusinessCode(); // 业务码（按档案规则）
String message = api.getMessage();      // 业务消息
Object elapsed = api.getElapsed();      // 耗时(ms)
```

## 七、编写 PO 风格测试

```java
package com.amsapi.tests;

import com.amsapi.api.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UserFlowTest {

    @Test
    void testUserLoginAndQuery() throws Exception {
        // 1. 登录
        LoginApi loginApi = new LoginApi();
        loginApi.login("admin", "123456");
        loginApi.checkSuccess(200, "登录成功");
        loginApi.extract("token=body.data.token");

        // 2. 查询用户列表
        AdminApi adminApi = new AdminApi();
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("page", 1);
        params.put("size", 10);
        adminApi.getUserList(params);
        adminApi.checkSuccess(200, "查询成功");
        adminApi.assertNotEmpty("body.data.records", "用户列表不为空");
    }
}
```

## 八、最佳实践

1. **优先用数据驱动**：简单接口测试用 YAML/Excel，不需要写 Java 代码
2. **PO 用于复杂流程**：多步骤、有业务逻辑的场景才写 API Object
3. **继承 BaseApi**：所有自定义接口对象必须继承 BaseApi
4. **不要硬编码地址**：URL 用相对路径，域名走配置
5. **用 `${var}` 传参**：接口间通过变量池传递，不要在代码中手动传
6. **使用 Constants 常量**：字段名、HTTP 方法、Content-Type 等均引用 `com.amsapi.common.Constants`，不要在代码中写字面量字符串（如 `"get"`、`"url"`、`"application/json"`）
