package com.amsapi.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import com.amsapi.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 日志工具。
 * 对应 Python 版 utils/logutil.py。
 * 使用 SLF4J + Logback，支持控制台 + 按天文件输出。
 */
public final class LogUtil {
    private static volatile boolean initialized = false;

    private static Logger _logger;

    public static Logger logger() {
        if (_logger == null) {
            try {
                _logger = LoggerFactory.getLogger(Settings.LogConfig.ROOT_NAME);
            } catch (Throwable t) {
                _logger = LoggerFactory.getLogger("amsapi");
            }
        }
        return _logger;
    }

    // 兼容旧引用
    public static final Logger logger = LoggerFactory.getLogger("amsapi");

    private LogUtil() {
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

        // 清除默认 appender
        rootLogger.detachAndStopAllAppenders();

        // 日志级别
        String level = Settings.LogConfig.LEVEL;
        rootLogger.setLevel(Level.toLevel(level, Level.DEBUG));

        // 格式
        String pattern = "%d{yyyy-MM-dd HH:mm:ss} | %-5level | %logger{36}:%line | %msg%n";

        // 控制台
        ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
        console.setContext(context);
        console.setName("CONSOLE");
        PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
        consoleEncoder.setContext(context);
        consoleEncoder.setPattern(pattern);
        consoleEncoder.start();
        console.setEncoder(consoleEncoder);
        console.start();
        rootLogger.addAppender(console);

        // 文件
        try {
            File logDir = new File(Settings.LOG_DIR);
            if (!logDir.exists()) logDir.mkdirs();
            String logFile = new File(logDir, Settings.LogConfig.fileName()).getAbsolutePath();

            FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
            fileAppender.setContext(context);
            fileAppender.setName("FILE");
            fileAppender.setFile(logFile);
            fileAppender.setAppend(true);
            PatternLayoutEncoder fileEncoder = new PatternLayoutEncoder();
            fileEncoder.setContext(context);
            fileEncoder.setPattern(pattern);
            fileEncoder.start();
            fileAppender.setEncoder(fileEncoder);
            fileAppender.start();
            rootLogger.addAppender(fileAppender);
        } catch (Exception e) {
            // 文件日志失败不影响运行
            System.err.println("日志文件初始化失败: " + e.getMessage());
        }
    }

    static {
        init();
    }
}
