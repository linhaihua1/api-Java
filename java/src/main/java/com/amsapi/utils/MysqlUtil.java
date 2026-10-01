package com.amsapi.utils;

import com.amsapi.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * MySQL 连接与执行工具。
 * 对应 Python 版 utils/mysqlutil.py。
 * 惰性连接：未配置时不连库。
 */
public class MysqlUtil implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MysqlUtil.class);
    private Connection connection;

    public MysqlUtil() throws SQLException {
        List<String> missing = Settings.missing_db_keys();
        if (!missing.isEmpty()) {
            throw new SQLException("MySQL 未配置必填项: " + missing);
        }
        String host = String.valueOf(Settings.DB_CONFIG.get("host"));
        int port = (int) Settings.DB_CONFIG.get("port");
        String database = String.valueOf(Settings.DB_CONFIG.get("database"));
        String user = String.valueOf(Settings.DB_CONFIG.get("user"));
        String password = String.valueOf(Settings.DB_CONFIG.get("password"));
        String charset = String.valueOf(Settings.DB_CONFIG.get("charset"));

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=" + charset + "&useSSL=false&serverTimezone=UTC";
        this.connection = DriverManager.getConnection(url, user, password);
        logger.info("MySQL 连接成功: {}/{}", host, database);
    }

    public Map<String, Object> getFetchOne(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepareStatement(sql, args);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rowToMap(rs);
            }
            return null;
        }
    }

    public List<Map<String, Object>> getFetchAll(String sql, Object... args) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement ps = prepareStatement(sql, args);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rowToMap(rs));
            }
        }
        return result;
    }

    public int executeUpdate(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepareStatement(sql, args)) {
            return ps.executeUpdate();
        }
    }

    private PreparedStatement prepareStatement(String sql, Object... args) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
        }
        return ps;
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }

    @Override
    public void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }
}
