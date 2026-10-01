# amsapi-auto 接口自动化测试框架

> 一套数据驱动、多系统适配的接口自动化测试框架，由 Python 版重构为 Java 版（Java 8 + JUnit 5 + Maven）。

## 一、框架简介

amsapi-auto 是一套生产级的接口自动化测试框架，核心设计理念是 **"一套框架适配多种 web 系统"**。

它把不同系统之间的差异（域名、认证方式、成功判定规则、报文格式编码、签名加密）从代码中抽离出来，放进一份**系统适配档案**（config/systems/*.ini）。换被测系统时，只需切换档案或新增档案，无需改动任何代码。

### 核心能力

| 能力 | 说明 |
|---|---|
| **数据驱动** | 支持 Excel / YAML / MySQL 三种数据源，用例与代码分离 |
| **系统适配** | 一套代码适配多种系统，差异收敛到配置档案 |
| **参数关联** | 接口间参数自动传递（`${var}` 占位符 + 变量池） |
| **统一认证** | 内置 7 种认证策略（none/basic/bearer/header/apikey/cookie/oauth2） |
| **响应判定** | 6 种成功判定风格（status/code/success/errno/retcode/raw） |
| **断言引擎** | 21 种字段级断言算子，路径化取值 |
| **协议钩子** | 请求前签名/时间戳、响应后解密，可配置化 |
| **Allure 报告** | 自动生成可视化测试报告 |

## 二、技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 语言 | Java | 8+ |
| 构建 | Maven | 3.6+ |
| 测试 | JUnit 5 | 5.10.2 |
| HTTP | Apache HttpClient | 4.5.14 |
| Excel | Apache POI | 5.2.5 |
| YAML | SnakeYAML | 2.2 |
| 数据库 | mysql-connector-j | 8.0.33 |
| 日志 | SLF4J + Logback | 2.0.13 / 1.5.6 |
| JSON | Jackson | 2.17.1 |
| 报告 | Allure | 2.27.0 |

## 三、快速开始

### 3.1 环境要求

- JDK 8+
- Maven 3.6+

### 3.2 编译

```bash
cd java
mvn clean compile
```

### 3.3 运行测试

```bash
# 运行全部测试
mvn test

# 只运行框架单元测试
mvn test -Dtest=CoreTest

# 运行指定测试类
mvn test -Dtest=TestYamlDriver
```

### 3.4 通过 Main 入口运行

```bash
mvn package
java -cp target/classes com.amsapi.Main
```

支持的命令行参数：
- `--smoke` — 只执行冒烟用例（标签为 smoke 的用例）
- `--report allure` — 指定报告类型

## 四、目录结构

```
java/
├── pom.xml                    # Maven 构建配置
├── config/                    # 配置文件（唯一配置入口）
│   ├── conf.ini               # 全局配置
│   └── systems/               # 系统适配档案
│       ├── default.ini        # 默认档案
│       └── mall.ini           # 商城系统档案示例
├── data/                      # 测试数据
│   └── cases/                 # YAML 用例目录
│       └── login.yaml         # 示例用例
├── src/
│   ├── main/java/com/amsapi/
│   │   ├── Main.java          # 程序入口
│   │   ├── config/            # 配置层
│   │   ├── common/            # 公共层（核心能力）
│   │   ├── utils/             # 工具层
│   │   └── api/               # 接口层（API Object）
│   └── test/java/com/amsapi/
│       ├── testcase/          # 数据驱动测试
│       └── tests/             # 框架单元测试
└── docs/                      # 本文档目录
```

## 五、文档索引

| 文档 | 内容 | 适合人群 |
|---|---|---|
| [01-架构设计](01-架构设计.md) | 分层架构、核心流程、调用链路 | 所有人 |
| [02-目录结构](02-目录结构.md) | 逐文件职责说明 | 开发人员 |
| [03-配置指南](03-配置指南.md) | conf.ini、环境变量、系统档案 | 测试人员、运维 |
| [04-用例编写](04-用例编写.md) | Excel/YAML 用例编写、参数化、断言 | 测试人员 |
| [05-API 层开发](05-API层开发.md) | BaseApi 使用、自定义接口对象 | 开发人员 |
| [06-系统适配指南](06-系统适配指南.md) | 多系统切换、认证、响应判定 | 架构师、高级测试 |
| [07-CI/CD 集成](07-CICD集成.md) | Jenkins 流水线、Allure 报告 | 运维、DevOps |
| [08-常见问题](08-常见问题FAQ.md) | FAQ、排错指南 | 所有人 |

## 六、一个最小示例

YAML 用例（`data/cases/login.yaml`）：

```yaml
cases:
  - id: 1001
    title: 登录并提取票据
    tags: [ smoke ]
    url: /v1/users/login
    method: get
    headers:
      Content-Type: application/json
    request_body:
      username: demo_user
      password: demo_password
    relation: ticket=body.data.ticket
    expected_code: 200
    expects: "type:body.data=dict; not_empty:body.data.ticket"
```

运行：

```bash
AMSAPI_BASE_URL=http://127.0.0.1:8080 AMSAPI_DATA_SOURCE=yaml mvn test -Dtest=TestYamlDriver
```

## 七、版本信息

- **版本**：1.0.0（Java 版）
- **生成日期**：2026-10-01
- **前身**：Python 版 amsapi-auto（已重构为 Java）
