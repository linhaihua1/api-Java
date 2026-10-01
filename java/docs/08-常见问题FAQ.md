# 08 - 常见问题 FAQ

## 一、编译与运行

### Q1: mvn compile 报错找不到依赖？

**A**: 检查网络连接，确保 Maven 能访问中央仓库。如需使用私服，在 `~/.m2/settings.xml` 中配置 mirror。

### Q2: 运行测试时提示 "未找到系统适配档案"？

**A**: 检查 `config/systems/` 目录下是否存在对应的 `.ini` 文件。可通过 `AMSAPI_SYSTEM=default` 使用默认档案（不需要任何配置）。

### Q3: 如何指定 Java 版本？

**A**: 框架要求 JDK 8+。确认 `JAVA_HOME` 指向正确的 JDK。

## 二、配置相关

### Q4: 环境变量和 conf.ini 谁优先？

**A**: 优先级：环境变量（`AMSAPI_*`） > `config/conf.ini` > 代码默认值。环境变量最优先，适合 CI 注入。

### Q5: 如何不修改仓库文件切换测试环境？

**A**: 使用环境变量：
```bash
AMSAPI_BASE_URL=http://test-server:8080 AMSAPI_DATA_SOURCE=yaml mvn test
```

### Q6: 如何关闭 SSL 证书验证？

**A**: `conf.ini` 中设置 `verify = false`，或环境变量 `AMSAPI_VERIFY=false`。

## 三、用例编写

### Q7: YAML 用例中如何引用上一个用例提取的变量？

**A**: 使用 `${变量名}` 占位符。例如：
```yaml
relation: token=body.data.token   # 提取
url: /v1/users/${token}/info      # 引用
```

### Q8: 断言路径怎么写？

**A**: 以完整响应为根，用点号分隔。响应结构为：
```
{
  "code": 200,         → code
  "body": {            → body
    "data": {          → body.data
      "list": [...]    → body.data.list
    }
  },
  "elapsed": 123.45,   → elapsed
  "headers": {...}     → headers
}
```
示例路径：`body.data.list.0.name`（列表第一个元素的 name 字段）

### Q9: expects 字段支持哪些断言？

**A**: 21 种算子，完整列表见 [04-用例编写](04-用例编写.md) 第 2.4 节。

### Q10: 如何跳过某个用例？

**A**: 在用例中设置 `check_business: false` 可跳过业务成功判定。如需完全跳过，可暂不添加到数据源，或使用标签过滤。

## 四、认证与适配

### Q11: 如何使用 OAuth2 认证？

**A**: 在系统档案中配置：
```ini
[auth]
type = oauth2
token_url = http://auth/token
client_id = xxx
client_secret = xxx
```
框架会自动取令牌、缓存、到期前刷新。

### Q12: Bearer Token 怎么从登录接口获取？

**A**: 登录用例提取 token 到变量池，后续请求自动带上：
```yaml
# 登录用例
relation: token=body.data.token

# 档案配置
[auth]
type = bearer
token = ${token}
```

### Q13: 系统返回的不是标准 JSON，怎么处理？

**A**: 可在档案中设置响应格式：
```ini
[env]
response_format = xml    # 自动解析 XML
# 或
response_format = text   # 纯文本不解析
```

### Q14: 多服务域名怎么配置？

**A**: 在档案 `[hosts]` 段定义别名，用例通过 `host` 字段指定：
```ini
[hosts]
order = http://order-service.com
```
```yaml
- url: /v1/orders
  host: order
```

## 五、排查问题

### Q15: 请求发送失败，怎么排查？

**A**: 查看日志输出，日志会打印：
- 请求方法和 URL
- 请求头（debug 级别）
- 响应状态码和耗时
- 响应体（debug 级别）

设置 `AMSAPI_LOG_LEVEL=DEBUG` 可查看更详细信息。

### Q16: 断言失败但看不出原因？

**A**: 断言错误信息会包含：
- 期望值和实际值
- 字段路径
- 响应摘要

可在 Allure 报告中查看完整的请求和响应附件。

### Q17: 变量没被替换怎么办？

**A**: 检查：
1. 变量名是否正确（大小写敏感）
2. 提取变量的用例是否在引用之前执行
3. 提取路径是否正确（可用 `body.xxx` 调试）

### Q18: 依赖的用例 skip 了？

**A**: 检查被依赖用例的执行结果。如果被依赖用例失败或未执行，当前用例会 skip。查看日志中的 `recordCaseStatus` 信息。

## 六、报告相关

### Q19: 如何生成 Allure 报告？

**A**: 
```bash
mvn test                          # 1. 运行测试
allure serve target/allure-results # 2. 直接在浏览器打开
# 或
allure generate target/allure-results -o report # 生成静态 HTML
```

### Q20: 报告中的请求/响应在哪里看？

**A**: Allure 报告中点击具体用例 → 右侧 "Attachments" 区域，包含 Request 和 Response 附件。

## 七、其他

### Q21: 框架支持并发执行吗？

**A**: 变量池（GlobalVar）是线程安全的，JUnit 5 支持并行执行。但需注意用例间的依赖关系（`depends`），有依赖的用例不应并行。

### Q22: 如何添加自定义断言？

**A**: 在 `Asserts.java` 中添加新方法，并在 `runOp` 的 switch 中注册算子名。

### Q23: 如何添加自定义钩子？

**A**: 见 [06-系统适配指南](06-系统适配指南.md) 第 6.3 节。实现一个返回 `Function<Map,Map>` 的静态方法，在档案中引用即可。
