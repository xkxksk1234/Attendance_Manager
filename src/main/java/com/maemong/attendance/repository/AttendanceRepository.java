package com.maemong.attendance.repository;

import com.maemong.attendance.model.AttendanceRecord;
import com.maemong.attendance.config.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttendanceRepository {

	public AttendanceRepository() {
		try {
			createTableIfNotExists();
			migrateAddDateIfMissing();     // ★ 1단계: date 보장
			migrateAddEmpNameIfMissing();   // ★ 추가
			migrateAddOutDateIfMissing();  // ★ 2단계: out_date 보장
			createIndexesIfColumnsExist(); // ★ 3단계: 인덱스는 컬럼 있을 때만
		} catch (SQLException e) {
			throw new RuntimeException("attendance 테이블 초기화 실패: " + e.getMessage(), e);
		}
	}

	/* ===================== Schema ===================== */

	private void createTableIfNotExists() throws SQLException {
		// 최신 스키마 정의(있으면 그대로 유지)
		String sql = """
            CREATE TABLE IF NOT EXISTS attendance (
              id        INTEGER PRIMARY KEY AUTOINCREMENT,
              emp_no    TEXT    NOT NULL,
              emp_name  TEXT,
              "date"    TEXT,              -- 입근일(YYYY-MM-DD)
              in_time   TEXT,              -- HH:mm:ss
              "out_date" TEXT,             -- 퇴근일(YYYY-MM-DD)
              out_time  TEXT,              -- HH:mm:ss
              memo      TEXT
            );
            """;
		try (Connection c = Database.getConnection();
		     Statement st = c.createStatement()) {
			st.execute(sql);
		}
	}

	/** PRAGMA로 컬럼 목록 조회 */
	private boolean hasColumn(String table, String col) throws SQLException {
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + table + ")");
		     ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				if (col.equalsIgnoreCase(rs.getString("name"))) return true;
			}
			return false;
		}
	}

	/** 구버전 DB에서 "date" 컬럼이 없으면 추가 */
	private void migrateAddDateIfMissing() throws SQLException {
		if (!hasColumn("attendance", "date")) {
			try (Connection c = Database.getConnection();
			     Statement st = c.createStatement()) {
				st.execute("ALTER TABLE attendance ADD COLUMN \"date\" TEXT;");
				// 적절한 초기화가 필요하면 여기에 UPDATE 추가 (알 수 없으면 NULL 유지)
			}
		}
	}

	/** "out_date" 컬럼이 없으면 추가 + 합리적 기본값 보정 */
	private void migrateAddOutDateIfMissing() throws SQLException {
		if (!hasColumn("attendance", "out_date")) {
			try (Connection c = Database.getConnection();
			     Statement st = c.createStatement()) {
				st.execute("ALTER TABLE attendance ADD COLUMN \"out_date\" TEXT;");
				// date가 있을 때만 보정. 함수/컬럼 충돌을 피하려고 "date"로 감쌈
				st.execute("""
                    UPDATE attendance
                    SET "out_date" = CASE
                        WHEN out_time IS NOT NULL AND in_time IS NOT NULL
                             AND time(out_time) < time(in_time)
                             THEN date("date", '+1 day')
                        ELSE "date"
                    END
                    WHERE "out_date" IS NULL
                """);
			}
		}
	}

	/** 인덱스는 컬럼이 있을 때만 생성 */
	private void createIndexesIfColumnsExist() throws SQLException {
		try (Connection c = Database.getConnection();
		     Statement st = c.createStatement()) {
			if (hasColumn("attendance", "date")) {
				st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_att_emp_date
                    ON attendance(emp_no, "date");
                """);
				st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_att_date
                    ON attendance("date");
                """);
			}
			if (hasColumn("attendance", "out_date")) {
				st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_att_out_date
                    ON attendance("out_date");
                """);
			}
		}
	}

	/* ===================== CRUD / Queries ===================== */

	/** 월별(입근일 기준) 조회: YYYY-MM */
	public List<AttendanceRecord> findByMonth(String ym) throws SQLException {
		String sql = """
            SELECT id, emp_no, emp_name, "date", in_time, "out_date", out_time, memo
            FROM attendance
            WHERE substr("date",1,7) = ?
            ORDER BY "date" ASC, in_time ASC, emp_no ASC, id ASC
            """;
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, ym);
			try (ResultSet rs = ps.executeQuery()) {
				List<AttendanceRecord> list = new ArrayList<>();
				while (rs.next()) list.add(map(rs));
				return list;
			}
		}
	}

	/** 일별 조회: 지정일이 입근일이거나 퇴근일인 건을 모두 반환 */
	public List<AttendanceRecord> findByDate(String ymd) throws SQLException {
		String sql = """
            SELECT id, emp_no, emp_name, "date", in_time, "out_date", out_time, memo
            FROM attendance
            WHERE "date" = ? OR "out_date" = ?
            ORDER BY "date" ASC, in_time ASC, emp_no ASC, id ASC
            """;
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, ymd);
			ps.setString(2, ymd);
			try (ResultSet rs = ps.executeQuery()) {
				List<AttendanceRecord> list = new ArrayList<>();
				while (rs.next()) list.add(map(rs));
				return list;
			}
		}
	}

	/** 출근 등록 */
	public long insertIn(String empNo, String empName, String date, String inTime, String memo) throws SQLException {
		String sql = """
            INSERT INTO attendance (emp_no, emp_name, "date", in_time, "out_date", out_time, memo)
            VALUES (?, ?, ?, ?, NULL, NULL, ?)
            """;
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, empNo);
			ps.setString(2, empName);
			ps.setString(3, date);
			ps.setString(4, inTime);
			ps.setString(5, memo);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				return keys.next() ? keys.getLong(1) : 0L;
			}
		}
	}

	/** 퇴근 등록 (id 기준) */
	public int updateOutById(long id, String outDate, String outTime, String memo) throws SQLException {
		String sql = """
            UPDATE attendance
            SET "out_date" = ?, out_time = ?, memo = COALESCE(?, memo)
            WHERE id = ?
            """;
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, outDate);
			ps.setString(2, outTime);
			ps.setString(3, memo);
			ps.setLong(4, id);
			return ps.executeUpdate();
		}
	}

	/** 퇴근 등록 (복합키) */
	public int updateOutByKey(String empNo, String date, String inTime, String outDate, String outTime, String memo) throws SQLException {
		String sql = """
            UPDATE attendance
            SET "out_date" = ?, out_time = ?, memo = COALESCE(?, memo)
            WHERE emp_no = ? AND "date" = ? AND in_time = ?
            """;
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, outDate);
			ps.setString(2, outTime);
			ps.setString(3, memo);
			ps.setString(4, empNo);
			ps.setString(5, date);
			ps.setString(6, inTime);
			return ps.executeUpdate();
		}
	}

	public AttendanceRecord findById(long id) throws SQLException {
		String sql = """
            SELECT id, emp_no, emp_name, "date", in_time, "out_date", out_time, memo
            FROM attendance WHERE id = ?
            """;
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? map(rs) : null;
			}
		}
	}

	public int deleteById(long id) throws SQLException {
		String sql = "DELETE FROM attendance WHERE id = ?";
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setLong(1, id);
			return ps.executeUpdate();
		}
	}

	public int deleteByKey(String empNo, String date, String inTime, String outTime) throws SQLException {
		String sql = """
            DELETE FROM attendance
            WHERE emp_no = ? AND "date" = ? AND in_time = ? AND (out_time IS ? OR out_time = ?)
            """;
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, empNo);
			ps.setString(2, date);
			ps.setString(3, inTime);
			if (outTime == null || outTime.isBlank()) {
				ps.setNull(4, Types.VARCHAR);
				ps.setString(5, ""); // 매칭 불가 값
			} else {
				ps.setNull(4, Types.VARCHAR);
				ps.setString(5, outTime);
			}
			return ps.executeUpdate();
		}
	}

	private boolean tableExists(String table) throws SQLException {
		try (Connection c = Database.getConnection();
		     PreparedStatement ps = c.prepareStatement(
				     "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
			ps.setString(1, table);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	/* ===================== Mapper ===================== */

	private AttendanceRecord map(ResultSet rs) throws SQLException {
		AttendanceRecord r = new AttendanceRecord();
		long id = rs.getLong("id");
		r.setId(rs.wasNull() ? null : id);
		r.setEmpNo(rs.getString("emp_no"));
		r.setEmpName(rs.getString("emp_name"));
		r.setDate(rs.getString("date"));
		r.setInTime(rs.getString("in_time"));
		r.setOutDate(rs.getString("out_date"));
		r.setOutTime(rs.getString("out_time"));
		r.setMemo(rs.getString("memo"));
		return r;
	}
}
