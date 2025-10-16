package com.maemong.attendance.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 출퇴근 기록 엔티티
 * - empNo    : 사번(필수)
 * - empName  : 이름(표시용, null 가능 – 조인/보강으로 채울 수 있음)
 * - date     : 근무일(입근일) yyyy-MM-dd
 * - inTime   : 출근시각 HH:mm:ss 또는 null
 * - outDate  : 퇴근일 yyyy-MM-dd (자정 넘김 처리용, 기본은 date와 동일)
 * - outTime  : 퇴근시각 HH:mm:ss 또는 null
 * - memo     : 메모(선택)
 * - id       : 레코드 식별자 (DB PK가 있다면 Long, 없으면 null)
 */
public class AttendanceRecord implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long id;           // DB PK (SQLite INTEGER PRIMARY KEY 등), 없으면 null
	private String empNo;      // 사번
	private String empName;    // 이름(표시용)
	private String date;       // yyyy-MM-dd (입근일)
	private String inTime;     // HH:mm:ss or null
	private String outDate;    // yyyy-MM-dd (퇴근일) ★
	private String outTime;    // HH:mm:ss or null
	private String memo;       // 메모

	public AttendanceRecord() {}

	/** 기존 시그니처(하위호환). outDate는 null로 두면 로직에서 date로 보정 */
	public AttendanceRecord(Long id, String empNo, String empName,
	                        String date, String inTime, String outTime, String memo) {
		this.id = id;
		this.empNo = empNo;
		this.empName = empName;
		this.date = date;
		this.inTime = inTime;
		this.outTime = outTime;
		this.memo = memo;
		this.outDate = null; // 서비스/리포지토리에서 null이면 date로 처리
	}

	/** 신규 시그니처: outDate 포함 */
	public AttendanceRecord(Long id, String empNo, String empName,
	                        String date, String inTime, String outDate, String outTime, String memo) {
		this.id = id;
		this.empNo = empNo;
		this.empName = empName;
		this.date = date;
		this.inTime = inTime;
		this.outDate = outDate;
		this.outTime = outTime;
		this.memo = memo;
	}

	/** 기존 시그니처(하위호환). outDate는 null로 두면 로직에서 date로 보정 */
	public AttendanceRecord(String empNo, String date, String inTime, String outTime, String memo) {
		this(null, empNo, null, date, inTime, outTime, memo);
	}

	/** 신규 시그니처: outDate 포함 */
	public AttendanceRecord(String empNo, String date, String inTime, String outDate, String outTime, String memo) {
		this(null, empNo, null, date, inTime, outDate, outTime, memo);
	}

	/* ===== Getters / Setters ===== */

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getEmpNo() { return empNo; }
	public void setEmpNo(String empNo) { this.empNo = empNo; }

	/** 조회 테이블의 이름 컬럼 표시용. 조인하지 않으면 null일 수 있음. */
	public String getEmpName() { return empName; }
	public void setEmpName(String empName) { this.empName = empName; }

	public String getDate() { return date; }
	public void setDate(String date) { this.date = date; }

	public String getInTime() { return inTime; }
	public void setInTime(String inTime) { this.inTime = inTime; }

	public String getOutDate() { return outDate; }          // ★ 추가
	public void setOutDate(String outDate) { this.outDate = outDate; } // ★ 추가

	public String getOutTime() { return outTime; }
	public void setOutTime(String outTime) { this.outTime = outTime; }

	public String getMemo() { return memo; }
	public void setMemo(String memo) { this.memo = memo; }

	/* ===== Utility ===== */

	/** 자정 넘김 여부 */
	public boolean isOvernight() {
		if (outDate == null || outDate.isBlank()) return false;
		return !Objects.equals(outDate, date);
	}

	/** 총 근무 분(출/퇴가 모두 있을 때만 계산) */
	public long getWorkMinutes() {
		if (inTime == null || outTime == null) return 0;
		var in = java.time.LocalDateTime.of(
				java.time.LocalDate.parse(date),
				java.time.LocalTime.parse(inTime)
		);
		var out = java.time.LocalDateTime.of(
				java.time.LocalDate.parse((outDate != null && !outDate.isBlank()) ? outDate : date),
				java.time.LocalTime.parse(outTime)
		);
		return java.time.Duration.between(in, out).toMinutes();
	}

	@Override
	public String toString() {
		return "AttendanceRecord{" +
				"id=" + id +
				", empNo='" + empNo + '\'' +
				", empName='" + empName + '\'' +
				", date='" + date + '\'' +
				", inTime='" + inTime + '\'' +
				", outDate='" + outDate + '\'' +
				", outTime='" + outTime + '\'' +
				", memo='" + memo + '\'' +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AttendanceRecord)) return false;
		AttendanceRecord that = (AttendanceRecord) o;
		// id가 있으면 id 기준
		if (id != null && that.id != null) return Objects.equals(id, that.id);
		// 없으면 복합키 근사 비교 (outDate 포함해야 자정 넘김 구분 가능)
		return Objects.equals(empNo, that.empNo)
				&& Objects.equals(date, that.date)
				&& Objects.equals(inTime, that.inTime)
				&& Objects.equals(outDate, that.outDate)
				&& Objects.equals(outTime, that.outTime);
	}

	@Override
	public int hashCode() {
		if (id != null) return Objects.hash(id);
		return Objects.hash(empNo, date, inTime, outDate, outTime);
	}
}
