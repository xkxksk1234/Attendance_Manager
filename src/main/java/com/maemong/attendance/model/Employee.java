package com.maemong.attendance.model;

/** 직원 엔티티 */
public class Employee {
    private String empNo;        // 사번 (PK)
    private String name;         // 이름
    private String position;     // 직급
    private String rrn;          // 주민등록번호
    private String phone;        // 전화번호
    private int    wage;         // 시급
    private String bank;         // 은행
    private String account;      // 계좌번호
    private String address;      // 주소
    private String contractDate; // 계약일 (yyyy-MM-dd)
    private String memo;         // 비고

    public Employee() {} // ✅ 무인자 생성자

    public Employee(String empNo, String name) {
        this.empNo = empNo;
        this.name = name;
    }

    public Employee(String empNo, String name, String position, int wage) {
        this.empNo = empNo;
        this.name = name;
        this.position = position;
        this.wage = wage;
    }

    /** ✅ AttendanceService에서 쓰는 전체 필드 생성자 */
    public Employee(String empNo, String name, String position,
                    String rrn, String phone, int wage,
                    String bank, String account, String address,
                    String contractDate, String memo) {
        this.empNo = empNo;
        this.name = name;
        this.position = position;
        this.rrn = rrn;
        this.phone = phone;
        this.wage = wage;
        this.bank = bank;
        this.account = account;
        this.address = address;
        this.contractDate = contractDate;
        this.memo = memo;
    }

    // ===== Getters / Setters =====
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public int getWage() { return wage; }
    public void setWage(int wage) { this.wage = wage; }

    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContractDate() { return contractDate; }
    public void setContractDate(String contractDate) { this.contractDate = contractDate; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}
