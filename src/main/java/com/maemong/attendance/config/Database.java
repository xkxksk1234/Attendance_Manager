package com.maemong.attendance.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class Database {

    /** 호출 시점의 점포 컨텍스트(StoreContext) 기준으로 연결 생성 */
    public static Connection getConnection() throws SQLException {
	    String dbPath = StoreContext.getDbFilePath();

	    // 상위 폴더 생성(혹시 누락돼 있을 수 있으니 안전하게)
	    try {
		    Path parent = Path.of(dbPath).getParent();
		    if (parent != null) Files.createDirectories(parent);
	    } catch (Exception ignored) {}

	    Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
	    try (Statement st = conn.createStatement()) {
		    st.execute("PRAGMA foreign_keys=ON;");
		    // ✅ 성능/안정성 추천 설정
		    st.execute("PRAGMA journal_mode=WAL;");
		    st.execute("PRAGMA synchronous=NORMAL;");
	    }

	    ensureSchemas(conn); // 기존 스키마 보정 로직 그대로 유지
	    return conn;
    }

    /* ───────── 스키마 보장/마이그레이션 ───────── */

    private static void ensureSchemas(Connection conn) throws SQLException {
        ensureEmployeesSchema(conn);
        ensureAttendanceSchema(conn); // UNIQUE(emp_no, work_date) 제거 마이그레이션 포함
    }

    private static void ensureEmployeesSchema(Connection conn) throws SQLException {
        String sql = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='employees'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) sql = rs.getString(1);
        }
        if (sql == null) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE employees (
                      emp_no      TEXT PRIMARY KEY,
                      name        TEXT NOT NULL,
                      position    TEXT,
                      rrn         TEXT,
                      phone       TEXT,
                      hourly_wage INTEGER DEFAULT 0,
                      bank        TEXT,
                      account_no  TEXT,
                      address     TEXT,
                      contract_date TEXT,
                      note        TEXT
                    )
                """);
            }
        }
    }
    
    private static void ensureAttendanceSchema(Connection conn) throws SQLException {
        // 현재 테이블 DDL
        String createSql = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='attendance'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) createSql = rs.getString(1);
        }

        boolean needCreate = (createSql == null);

        // 현재 컬럼 목록 조회
        java.util.Set<String> cols = new java.util.HashSet<>();
        if (!needCreate) {
            try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(attendance)");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.add(rs.getString("name"));
            }
        }

        boolean hasIdCol = cols.contains("id"); // ← 핵심: id 유무
        boolean hasUniquePerDay = (createSql != null && createSql.contains("UNIQUE(emp_no, work_date)"));

        if (needCreate) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE attendance (
                      id        INTEGER PRIMARY KEY AUTOINCREMENT,
                      emp_no    TEXT NOT NULL,
                      work_date TEXT NOT NULL,   -- yyyy-MM-dd
                      clock_in  TEXT,            -- HH:mm:ss
                      clock_out TEXT,            -- HH:mm:ss
                      memo      TEXT,
                      FOREIGN KEY(emp_no) REFERENCES employees(emp_no)
                    )
                """);
            }
            return;
        }

        // id 컬럼이 없거나, UNIQUE(emp_no, work_date) 제약이 있으면 재생성 + 데이터 이관
        if (!hasIdCol || hasUniquePerDay) {
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys=OFF;");
                st.execute("BEGIN TRANSACTION;");
                st.execute("""
                    CREATE TABLE attendance_new (
                      id        INTEGER PRIMARY KEY AUTOINCREMENT,
                      emp_no    TEXT NOT NULL,
                      work_date TEXT NOT NULL,
                      clock_in  TEXT,
                      clock_out TEXT,
                      memo      TEXT,
                      FOREIGN KEY(emp_no) REFERENCES employees(emp_no)
                    )
                """);

                // 기존에 id가 없을 수도 있으므로 컬럼을 명시하지 않고 복사
                st.execute("""
                    INSERT INTO attendance_new (emp_no, work_date, clock_in, clock_out, memo)
                    SELECT emp_no, work_date, clock_in, clock_out, memo
                    FROM attendance
                """);

                st.execute("DROP TABLE attendance;");
                st.execute("ALTER TABLE attendance_new RENAME TO attendance;");
                st.execute("COMMIT;");
                st.execute("PRAGMA foreign_keys=ON;");
            }
        }
    }
}
