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

	// AttendanceRepository.java 내부
	private void migrateAddEmpNameIfMissing() {
		try (java.sql.Connection conn = com.maemong.attendance.config.Database.getConnection();
		     java.sql.Statement st = conn.createStatement()) {

			// emp_name 컬럼 존재 여부 확인
			boolean exists = false;
			try (java.sql.ResultSet rs = st.executeQuery("PRAGMA table_info(attendance)")) {
				while (rs.next()) {
					if ("emp_name".equalsIgnoreCase(rs.getString("name"))) {
						exists = true;
						break;
					}
				}
			}

			if (!exists) {
				// 1) 컬럼 추가
				st.executeUpdate("ALTER TABLE attendance ADD COLUMN emp_name TEXT");

				// 2) employees에서 이름 백필
				//    ※ 별칭 사용 금지, 바깥 테이블은 'attendance'로 직접 참조
				st.executeUpdate(
						"UPDATE attendance " +
								"SET emp_name = (" +
								"  SELECT e.name FROM employees e " +
								"  WHERE e.emp_no = attendance.emp_no" +
								") " +
								"WHERE emp_name IS NULL"
				);

				// 3) 선택: 인덱스
				st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_att_emp_name ON attendance(emp_name)");
			}

		} catch (Exception ex) {
			ex.printStackTrace(); // 마이그레이션 실패해도 앱 중단 X
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
	// AttendanceRepository.java 내부

	// 컬럼 존재 여부 유틸(중복 없으면 추가)
	private boolean hasColumn(java.sql.Connection conn, String table, String column) throws java.sql.SQLException {
		try (java.sql.PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")");
		     java.sql.ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				if (column.equalsIgnoreCase(rs.getString("name"))) return true;
			}
		}
		return false;
	}

	// AttendanceRepository.java 내부에 추가
	private boolean hasColumn(String table, String column) throws java.sql.SQLException {
		try (java.sql.Connection conn = com.maemong.attendance.config.Database.getConnection()) {
			return hasColumn(conn, table, column); // 3-인자 버전으로 위임
		}
	}

	// Repository의 공개 API — Service는 이걸 호출해야 함
	public void clockIn(String empNo, String workDate, String inTime, String memo) throws java.sql.SQLException {
		insertIn(empNo, workDate, inTime, memo);  // private 메서드 위임
	}

	public void clockOut(String empNo, String outDate, String outTime) throws java.sql.SQLException {
		// 가장 최근 미퇴근 1건에 퇴근 시간/날짜 채우기
		try (java.sql.Connection conn = com.maemong.attendance.config.Database.getConnection()) {
			boolean hasOutDate = hasColumn(conn, "attendance", "out_date");
			if (hasOutDate) {
				try (java.sql.PreparedStatement ps = conn.prepareStatement(
						"UPDATE attendance SET out_time=?, out_date=? " +
								"WHERE id = (SELECT id FROM attendance WHERE emp_no=? AND out_time IS NULL ORDER BY id DESC LIMIT 1)"
				)) {
					ps.setString(1, outTime);
					ps.setString(2, outDate);
					ps.setString(3, empNo);
					ps.executeUpdate();
				}
			} else {
				try (java.sql.PreparedStatement ps = conn.prepareStatement(
						"UPDATE attendance SET out_time=? " +
								"WHERE id = (SELECT id FROM attendance WHERE emp_no=? AND out_time IS NULL ORDER BY id DESC LIMIT 1)"
				)) {
					ps.setString(1, outTime);
					ps.setString(2, empNo);
					ps.executeUpdate();
				}
			}
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
	// AttendanceRepository.java 내부
	// 시그니처가 이미 (String empNo, String workDate, String inTime, String memo) 라면
	// 본문만 아래로 교체. 시그니처가 다르면 호출부 기준에 맞춰 파라미터 이름만 맞게 바꿔 주세요.
	private void insertIn(String empNo, String workDate, String inTime, String memo) throws java.sql.SQLException {
		try (java.sql.Connection conn = com.maemong.attendance.config.Database.getConnection();
		     java.sql.PreparedStatement ps = conn.prepareStatement(
				     // ✅ 스키마 컬럼명과 일치시키기: "date"
				     "INSERT INTO attendance (emp_no, \"date\", in_time, memo) VALUES (?, ?, ?, ?)"
		     )) {
			ps.setString(1, empNo);
			ps.setString(2, workDate);  // YYYY-MM-DD
			ps.setString(3, inTime);    // HH:mm:ss
			ps.setString(4, memo);
			ps.executeUpdate();
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
