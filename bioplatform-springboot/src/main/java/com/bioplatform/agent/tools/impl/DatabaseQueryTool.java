package com.bioplatform.agent.tools.impl;

import com.bioplatform.agent.tools.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 数据库查询工具 - 让 Agent 能直接查询 MySQL 数据库
 * 只允许 SELECT 查询，禁止写操作
 *
 * @author luosg
 */
@Component
public class DatabaseQueryTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryTool.class);
    private static final int MAX_ROWS = 100;
    private static final int MAX_OUTPUT_CHARS = 8192;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public DatabaseQueryTool(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "database_query";
    }

    @Override
    public String getDescription() {
        return "查询平台 MySQL 数据库（bioplatform），执行 SELECT 查询并返回结果。" +
                "可用于：查看项目列表、统计记录数、查询用户信息、查看分析任务状态等。" +
                "只支持 SELECT 查询，禁止 INSERT/UPDATE/DELETE/DROP 等写操作。" +
                "最多返回 " + MAX_ROWS + " 行数据。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sqlProp = new HashMap<>();
        sqlProp.put("type", "string");
        sqlProp.put("description", "要执行的 SQL SELECT 查询语句");
        properties.put("sql", sqlProp);

        schema.put("properties", properties);
        schema.put("required", List.of("sql"));
        return schema;
    }

    @Override
    public String execute(Map<String, String> args) {
        String sql = args.get("sql");
        if (sql == null || sql.isBlank()) {
            return toJson(-1, "缺少必需参数: sql");
        }

        // 安全校验：只允许 SELECT 和 SHOW
        String trimmed = sql.strip().toUpperCase();
        if (!trimmed.startsWith("SELECT") && !trimmed.startsWith("SHOW") && !trimmed.startsWith("DESCRIBE")) {
            return toJson(-1, "只允许 SELECT/SHOW/DESCRIBE 查询，禁止写操作");
        }

        // 额外拦截危险关键词
        String upper = sql.toUpperCase();
        for (String keyword : List.of("INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE", "GRANT", "REVOKE")) {
            if (upper.contains(keyword)) {
                return toJson(-1, "SQL 包含禁止的关键字: " + keyword);
            }
        }

        log.info("database_query: sql={}", sql);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.setMaxRows(MAX_ROWS);
            stmt.setQueryTimeout(30);

            boolean hasResultSet = stmt.execute(sql);
            if (!hasResultSet) {
                return toJson(0, "查询执行成功（无返回数据）");
            }

            try (ResultSet rs = stmt.getResultSet()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                // 收集列名
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }

                // 收集行数据
                List<List<Object>> rows = new ArrayList<>();
                int rowCount = 0;
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                    rowCount++;
                }

                // 构建结果
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("exit_code", 0);
                result.put("success", true);
                result.put("columns", columns);
                result.put("row_count", rowCount);
                result.put("rows", rows);

                String json = objectMapper.writeValueAsString(result);
                if (json.length() > MAX_OUTPUT_CHARS) {
                    // 截断行数
                    int keepRows = Math.max(1, rowCount * MAX_OUTPUT_CHARS / json.length());
                    rows = rows.subList(0, Math.min(keepRows, rows.size()));
                    result.put("rows", rows);
                    result.put("row_count", rows.size());
                    result.put("truncated", true);
                    json = objectMapper.writeValueAsString(result);
                }

                return json;
            }
        } catch (SQLException e) {
            log.error("database_query 执行失败: {}", e.getMessage());
            return toJson(-1, "SQL 执行失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("database_query 异常", e);
            return toJson(-1, "查询异常: " + e.getMessage());
        }
    }

    private String toJson(int exitCode, String message) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("exit_code", exitCode);
            result.put("success", exitCode == 0);
            result.put("output", message);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"exit_code\":" + exitCode + ",\"output\":\"" + message.replace("\"", "\\\"") + "\"}";
        }
    }
}
