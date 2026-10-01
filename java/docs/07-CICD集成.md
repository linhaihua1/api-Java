# 07 - CI/CD 集成指南

## 一、Jenkins 流水线

> 框架兼容 Java 8 / 11 / 17，默认编译为 Java 8 字节码。CI 中可根据环境选择 JDK 版本，无需特殊配置即可运行。如需以更高版本编译，可加 `-P java11` 或 `-P java17`。

### 1.1 声明式 Pipeline

```groovy
pipeline {
    agent any

    environment {
        // 环境变量注入（不修改仓库文件）
        AMSAPI_BASE_URL    = "${env.BASE_URL}"
        AMSAPI_SYSTEM      = "${env.SYSTEM ?: 'default'}"
        AMSAPI_DATA_SOURCE = "${env.DATA_SOURCE ?: 'yaml'}"
        AMSAPI_SMOKE       = "${env.SMOKE ?: 'false'}"
        AMSAPI_REPORT_TYPE = "allure"
    }

    stages {
        stage('编译') {
            steps {
                sh 'mvn clean compile -q'
            }
        }

        stage('冒烟测试') {
            when {
                environment name: 'SMOKE', value: 'true'
            }
            steps {
                sh 'mvn test -Dtest=TestYamlDriver'
            }
        }

        stage('全量测试') {
            when {
                not { environment name: 'SMOKE', value: 'true' }
            }
            steps {
                sh 'mvn test'
            }
        }
    }

    post {
        always {
            // Allure 报告
            allure includeProperties: false,
                   jdk: '',
                   results: [[path: 'target/allure-results']]
        }
        failure {
            echo '测试失败，请查看报告'
        }
    }
}
```

### 1.2 关键环境变量

| 变量 | 说明 | 示例值 |
|---|---|---|
| `BASE_URL` | 被测系统地址 | `http://test-server:8080` |
| `SYSTEM` | 系统档案名 | `mall` |
| `DATA_SOURCE` | 数据源 | `yaml` |
| `SMOKE` | 是否冒烟模式 | `true` / `false` |

### 1.3 退出码

测试全部通过 → 退出码 0
有失败用例 → 退出码非 0

Jenkins 可直接用退出码判断流水线成败。

## 二、Allure 报告

### 2.1 生成报告

```bash
# 1. 运行测试（自动生成 allure-results）
mvn test

# 2. 生成 HTML 报告（需安装 allure 命令行）
allure generate target/allure-results -o target/allure-report --clean

# 3. 打开报告
allure open target/allure-report
```

### 2.2 Allure 命令行安装

```bash
# Mac
brew install allure

# Windows (Scoop)
scoop install allure

# 或下载发行版
# https://github.com/allure-framework/allure2/releases
```

### 2.3 报告内容

Allure 报告自动包含：
- 用例执行结果（通过/失败/跳过）
- 请求详情（方法、URL、Headers、Body）
- 响应详情（状态码、耗时、响应体）
- 失败用例的断言错误信息
- 用例标签（冒烟/回归等）

## 三、JUnit XML 报告

Maven Surefire 自动生成 JUnit XML 格式报告，位于：
```
target/surefire-reports/TEST-*.xml
```

可被 Jenkins、GitLab CI 等直接解析。

## 四、GitLab CI 示例

```yaml
stages:
  - test

api-test:
  stage: test
  image: maven:3.8-openjdk-8    # 可选 maven:3.8-openjdk-11 / maven:3.8-openjdk-17
  variables:
    AMSAPI_BASE_URL: "http://test-server:8080"
    AMSAPI_DATA_SOURCE: "yaml"
  script:
    - mvn test
  artifacts:
    when: always
    paths:
      - target/surefire-reports/
      - target/allure-results/
    reports:
      junit: target/surefire-reports/TEST-*.xml
```

## 五、GitHub Actions 示例

```yaml
name: API Test
on: [push]
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [ '8', '11', '17' ]    # 多版本兼容验证
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: ${{ matrix.java }}
          distribution: 'temurin'
          cache: maven
      - run: cd java && mvn test
        env:
          AMSAPI_BASE_URL: ${{ secrets.TEST_BASE_URL }}
          AMSAPI_DATA_SOURCE: yaml
      - uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-reports
          path: java/target/surefire-reports/
```

## 六、最佳实践

1. **敏感信息走环境变量**：地址、账号、密码不要写在仓库文件里
2. **冒烟 + 全量分离**：PR 合并前跑冒烟，定时任务跑全量
3. **报告归档**：allure-results 归档，便于追溯历史
4. **退出码判定**：CI 直接用退出码，不需要解析输出
