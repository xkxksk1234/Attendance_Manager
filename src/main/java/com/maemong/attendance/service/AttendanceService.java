package com.maemong.attendance.service;

import com.maemong.attendance.model.AttendanceRecord;
import com.maemong.attendance.model.Employee;
import com.maemong.attendance.repository.AttendanceRepository;
import com.maemong.attendance.repository.EmployeeRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 근태 서비스 (out_date 지원 + 하위호환 오버로드 포함)
 * - 출근/퇴근 등록
 * - 자정 넘김(out_date) 자동 보정
 * - 기존 UI가 호출하던 clockIn/clockOut/clockInAt/clockOutAt 시그니처 모두 지원
 */
public class AttendanceService {

	private final AttendanceRepository attendanceRepo = new AttendanceRepository();
	private final EmployeeRepository employeeRepo = new EmployeeRepository();

	/* =========================================================
	 * 출근 등록
	 * ========================================================= */

	/** 기존 시그니처: 출근 등록 (empNo, date, inTime, memo) */
	public long clockIn(String empNo, String date, String inTime, String memo) {
		String empName = lookupName(empNo);
		try {
			return attendanceRepo.insertIn(empNo, empName, date, inTime, memo);
		} catch (Exception e) {
			throw new RuntimeException("출근 등록 실패: " + e.getMessage(), e);
		}
	}

	/** 하위호환: 기존 UI가 호출하는 메서드명 지원 */
	public long clockInAt(String empNo, String date, String inTime, String memo) {
		return clockIn(empNo, date, inTime, memo);
	}

	/* =========================================================
	 * 퇴근 등록 (자정 넘김 자동 보정)
	 * ========================================================= */

	/** 기존 시그니처: 퇴근 등록 (empNo, date, outTime) */
	public int clockOut(String empNo, String date, String outTime) {
		return clockOut(empNo, date, outTime, null);
	}

	/** 기존+: 퇴근 등록 (empNo, date, outTime, memo) */
	public int clockOut(String empNo, String date, String outTime, String memo) {
		try {
			// 지정 'date'에 해당하는 미완료(퇴근 미기재) 출근 기록 중 가장 최근 inTime을 선택
			List<AttendanceRecord> list = attendanceRepo.findByDate(date); // date = ? OR out_date = ?
			AttendanceRecord open = list.stream()
					.filter(r -> Objects.equals(empNo, r.getEmpNo()))
					.filter(r -> r.getOutTime() == null || r.getOutTime().isBlank())
					.max(Comparator.comparing(r -> safeTime(r.getInTime())))
					.orElse(null);

			if (open == null || open.getId() == null) {
				throw new IllegalStateException("해당 날짜에 미완료 출근 기록을 찾을 수 없습니다. (사번: " + empNo + ", 날짜: " + date + ")");
			}

			String inTime = open.getInTime();
			String outDate = computeOutDate(date, inTime, outTime); // 자정 넘김 자동 보정
			return attendanceRepo.updateOutById(open.getId(), outDate, outTime, memo);
		} catch (Exception e) {
			throw new RuntimeException("퇴근 등록 실패: " + e.getMessage(), e);
		}
	}

	/** 하위호환: 기존 UI가 호출하는 메서드명 지원 */
	public int clockOutAt(String empNo, String date, String outTime) {
		return clockOut(empNo, date, outTime);
	}

	/** 하위호환(메모 포함 버전이 필요할 경우) */
	public int clockOutAt(String empNo, String date, String outTime, String memo) {
		return clockOut(empNo, date, outTime, memo);
	}

	/* =========================================================
	 * 퇴근 등록 (복합키로 특정 건 지정)
	 * ========================================================= */

	/**
	 * 복합키 기반 퇴근 등록:
	 * - empNo + date(입근일) + inTime 로 특정 레코드를 지정해 업데이트
	 * - outDate가 null/blank면 in/out 시간 비교로 자동 보정
	 */
	public int clockOutByKey(String empNo, String date, String inTime,
	                         String outDate, String outTime, String memo) {
		try {
			String finalOutDate = (outDate == null || outDate.isBlank())
					? computeOutDate(date, inTime, outTime)
					: outDate;
			return attendanceRepo.updateOutByKey(empNo, date, inTime, finalOutDate, outTime, memo);
		} catch (Exception e) {
			throw new RuntimeException("퇴근 등록(복합키) 실패: " + e.getMessage(), e);
		}
	}

	/* =========================================================
	 * 내부 유틸
	 * ========================================================= */

	/** 사번으로 이름 조회 (없으면 null) */
	private String lookupName(String empNo) {
		try {
			Employee e = employeeRepo.findByEmpNo(empNo);
			return e != null ? e.getName() : null;
		} catch (Exception ignore) {
			return null;
		}
	}

	/** outDate 자동 보정: outTime < inTime 이면 date + 1일, 아니면 동일 날짜 */
	private String computeOutDate(String date, String inTime, String outTime) {
		LocalDate d = LocalDate.parse(date);
		LocalTime in = safeTime(inTime);
		LocalTime out = safeTime(outTime);
		if (in != null && out != null && out.isBefore(in)) {
			return d.plusDays(1).toString();
		}
		return date;
	}

	/** null/blank 안전 LocalTime 파서 */
	private LocalTime safeTime(String t) {
		if (t == null || t.isBlank()) return null;
		return LocalTime.parse(t);
	}
}
