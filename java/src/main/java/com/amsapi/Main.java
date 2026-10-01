package com.amsapi;

import com.amsapi.common.Constants;
import com.amsapi.common.profile.SystemProfile;
import com.amsapi.common.variable.GlobalVar;
import com.amsapi.config.Settings;
import com.amsapi.utils.LogUtil;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * 测试入口。
 * 对应 Python 版 main.py。
 *
 * 用法：
 *   java -jar api-auto.jar                  # 按 conf.ini 执行全部用例
 *   java -jar api-auto.jar --smoke          # 只执行冒烟用例
 *   java -jar api-auto.jar --report allure  # 生成 Allure 报告
 */
public class Main {

    private static final String[] VALID_REPORT_TYPES = Constants.VALID_REPORT_TYPES;

    public static void main(String[] args) {
        // 初始化日志
        LogUtil.init();

        // 解析参数
        String reportType = Settings.RunConfig.REPORT_TYPE;
        boolean smoke = Settings.RunConfig.SMOKE_ONLY;
        List<String> junitArgs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--report".equals(arg) && i + 1 < args.length) {
                reportType = args[++i].trim().toLowerCase();
            } else if (arg.startsWith("--report=")) {
                reportType = arg.split("=", 2)[1].trim().toLowerCase();
            } else if ("--smoke".equals(arg)) {
                smoke = true;
            } else {
                junitArgs.add(arg);
            }
        }

        // 写回配置
        Settings.RunConfig.REPORT_TYPE = reportType;
        Settings.RunConfig.SMOKE_ONLY = smoke;

        // 打印启动信息
        String sep = repeat('=', 70);
        System.out.println(sep);
        System.out.println(Settings.ReportConfig.TITLE + " 启动");
        System.out.println("项目根目录 : " + Settings.BASE_DIR);
        System.out.println("运行环境   : " + Settings.RunConfig.ENV);
        System.out.println("系统档案   : " + Settings.RunConfig.SYSTEM);
        System.out.println("执行模式   : " + (smoke ? "冒烟（标签 " + Settings.RunConfig.TAGS + "）" : "全量"));
        System.out.println("数据源     : " + Settings.RunConfig.DATA_SOURCE);
        System.out.println("结果回写   : " + Settings.RunConfig.WRITE_BACK);
        System.out.println("报告类型   : " + reportType);
        System.out.println(sep);

        // 清空变量池
        GlobalVar.clear();

        // 加载系统档案
        SystemProfile profile = SystemProfile.loadProfile();
        System.out.println("系统适配信息: " + profile.describe());

        // 执行测试
        LauncherDiscoveryRequest request = request()
                .selectors(selectPackage("com.amsapi.testcase"))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out));

        System.out.println(sep);
        System.out.println("测试结束：通过 " + summary.getTestsSucceededCount() +
                "，失败 " + summary.getTestsFailedCount() +
                "，跳过 " + summary.getTestsSkippedCount());
        System.out.println(sep);

        System.exit(summary.getTestsFailedCount() > 0 ? 1 : 0);
    }

    /** Java 8 兼容的字符串重复方法（替代 String.repeat） */
    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
