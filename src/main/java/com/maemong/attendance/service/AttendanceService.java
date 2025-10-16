package com.maemong.attendance.service;

import com.maemong.attendance.model.AttendanceRecord;
import com.maemong.attendance.repository.AttendanceRepository;
import com.maemong.attendance.repository.EmployeeRepository;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

public class AttendanceService {

	private final EmployeeRepository employeeRepo = new EmployeeRepository();
	private final AttendanceRepository attendanceRepo = new AttendanceRepository();

	// ✅ 누락됐던 상수 정의
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("uuuu-MM-dd")
			.withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm:ss");

    /* ====================
       출근/퇴근 등록
       ==================== */

	/** 지금 출근 */
	public void clockIn(String empNo, String memo) throws SQLException {
		ZonedDateTime now = ZonedDateTime.now(KST);
		attendanceRepo.clockIn(empNo, now.format(D), now.format(T), memo);
	}

	/** 지정일/시각 출근 (비어있으면 현재로 보정) */
	public void clockInAt(String empNo, String yyyyMMdd, String hhmmOrHhmmss, String memo) throws SQLException {
		ZonedDateTime now = ZonedDateTime.now(KST);
		String date = (yyyyMMdd == null || yyyyMMdd.isBlank()) ? now.format(D) : normalizeDate(yyyyMMdd);
		String time = (hhmmOrHhmmss == null || hhmmOrHhmmss.isBlank()) ? now.format(T) : normalizeTime(hhmmOrHhmmss);
		attendanceRepo.clockIn(empNo, date, time, memo);
	}

	/** 지금 퇴근 */
	public void clockOut(String empNo) throws SQLException {
		ZonedDateTime now = ZonedDateTime.now(KST);
		attendanceRepo.clockOut(empNo, now.format(D), now.format(T));
	}

	/** 지정일/시각 퇴근 (비어있으면 현재로 보정) */
	public void clockOutAt(String empNo, String yyyyMMdd, String hhmmOrHhmmss) throws SQLException {
		ZonedDateTime now = ZonedDateTime.now(KST);
		String date = (yyyyMMdd == null || yyyyMMdd.isBlank()) ? now.format(D) : normalizeDate(yyyyMMdd);
		String time = (hhmmOrHhmmss == null || hhmmOrHhmmss.isBlank()) ? now.format(T) : normalizeTime(hhmmOrHhmmss);
		attendanceRepo.clockOut(empNo, date, time);
	}

    /* ====================
       조회
       ==================== */

	public List<AttendanceRecord> todayRecords() throws SQLException {
		String today = ZonedDateTime.now(KST).format(D);
		return attendanceRepo.findByDate(today);
	}

	public List<AttendanceRecord> recordsAt(String yyyyMMdd) throws SQLException {
		return attendanceRepo.findByDate(normalizeDate(yyyyMMdd));
	}

	public List<AttendanceRecord> recordsInMonth(String yyyyMM) throws SQLException {
		YearMonth.parse(yyyyMM); // "uuuu-MM" 형식 엄격 검증
		return attendanceRepo.findByMonth(yyyyMM);
	}

    /* ====================
       보조
       ==================== */

	private static String normalizeDate(String s) {
		LocalDate.parse(s, D); // 형식 검증
		return s;
	}

	private static String normalizeTime(String s) {
		if (s.matches("^\\d{2}:\\d{2}$")) return s + ":00";
		if (s.matches("^\\d{2}:\\d{2}:\\d{2}$")) return s;
		throw new IllegalArgumentException("시각 형식은 HH:mm 또는 HH:mm:ss 이어야 합니다.");
	}
}