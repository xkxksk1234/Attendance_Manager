package com.maemong.attendance.repository;

import com.maemong.attendance.config.Database;
import com.maemong.attendance.model.Employee;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 직원 레포지토리 (CRUD + upsert, updateWage) */
public class EmployeeRepository {

    public EmployeeRepository() {
        ensureSchema();
    }

    /* ===== 스키마 생성/보정 ===== */
    private void ensureSchema() {
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS employees(
                  emp_no TEXT PRIMARY KEY,
                  name   TEXT,
                  position TEXT,
                  rrn    TEXT,
                  phone  TEXT,
                  wage   INTEGER DEFAULT 0,
                  bank   TEXT,
                  account TEXT,
                  address TEXT,
                  contract_date TEXT,
                  memo   TEXT
                )
                """);
            addColumnIfMissing(c, "employees", "position",      "TEXT");
            addColumnIfMissing(c, "employees", "rrn",           "TEXT");
            addColumnIfMissing(c, "employees", "phone",         "TEXT");
            addColumnIfMissing(c, "employees", "wage",          "INTEGER DEFAULT 0");
            addColumnIfMissing(c, "employees", "bank",          "TEXT");
            addColumnIfMissing(c, "employees", "account",       "TEXT");
            addColumnIfMissing(c, "employees", "address",       "TEXT");
            addColumnIfMissing(c, "employees", "contract_date", "TEXT");
            addColumnIfMissing(c, "employees", "memo",          "TEXT");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addColumnIfMissing(Connection c, String table, String col, String type) throws SQLException {
        boolean exists = false;
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (col.equalsIgnoreCase(rs.getString("name"))) { exists = true; break; }
                }
            }
        }
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + type);
            }
        }
    }

    /* ===== CRUD ===== */

    public List<Employee> listAll() throws SQLException {
        ensureSchema();
        List<Employee> list = new ArrayList<>();
        String sql = "SELECT emp_no, name, position, rrn, phone, wage, bank, account, address, contract_date, memo " +
                     "FROM employees ORDER BY emp_no ASC";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Employee findByEmpNo(String empNo) throws SQLException {
        ensureSchema();
        String sql = "SELECT emp_no, name, position, rrn, phone, wage, bank, account, address, contract_date, memo " +
                     "FROM employees WHERE emp_no = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, empNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public void insert(Employee e) throws SQLException {
        ensureSchema();
        String sql = """
            INSERT INTO employees
              (emp_no, name, position, rrn, phone, wage, bank, account, address, contract_date, memo)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bindInsert(ps, e);
            ps.executeUpdate();
        }
    }

    public void update(Employee e) throws SQLException {
        ensureSchema();
        String sql = """
            UPDATE employees SET
              name=?, position=?, rrn=?, phone=?, wage=?, bank=?, account=?, address=?, contract_date=?, memo=?
            WHERE emp_no=?
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bindUpdate(ps, e);
            ps.executeUpdate();
        }
    }

    public void deleteByEmpNo(String empNo) throws SQLException {
        ensureSchema();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM employees WHERE emp_no=?")) {
            ps.setString(1, empNo);
            ps.executeUpdate();
        }
    }

    /* ===== AttendanceService가 기대하는 메서드 ===== */

    /** ✅ upsert: 있으면 UPDATE, 없으면 INSERT */
    public void upsert(Employee e) throws SQLException {
        if (findByEmpNo(e.getEmpNo()) == null) insert(e);
        else update(e);
    }

    /** ✅ 시급만 갱신 */
    public void updateWage(String empNo, int wage) throws SQLException {
        ensureSchema();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE employees SET wage=? WHERE emp_no=?")) {
            ps.setInt(1, wage);
            ps.setString(2, empNo);
            ps.executeUpdate();
        }
    }

    /* ===== helpers ===== */
    private Employee map(ResultSet rs) throws SQLException {
        return new Employee(
                rs.getString(1), // emp_no
                rs.getString(2), // name
                rs.getString(3), // position
                rs.getString(4), // rrn
                rs.getString(5), // phone
                rs.getInt(6),    // wage
                rs.getString(7), // bank
                rs.getString(8), // account
                rs.getString(9), // address
                rs.getString(10),// contract_date
                rs.getString(11) // memo
        );
    }

    private static String n(String s){ return s==null? "" : s; }

    private void bindInsert(PreparedStatement ps, Employee e) throws SQLException {
        ps.setString(1,  e.getEmpNo());
        ps.setString(2,  n(e.getName()));
        ps.setString(3,  n(e.getPosition()));
        ps.setString(4,  n(e.getRrn()));
        ps.setString(5,  n(e.getPhone()));
        ps.setInt(6,     e.getWage());
        ps.setString(7,  n(e.getBank()));
        ps.setString(8,  n(e.getAccount()));
        ps.setString(9,  n(e.getAddress()));
        ps.setString(10, n(e.getContractDate()));
        ps.setString(11, n(e.getMemo()));
    }

    private void bindUpdate(PreparedStatement ps, Employee e) throws SQLException {
        ps.setString(1,  n(e.getName()));
        ps.setString(2,  n(e.getPosition()));
        ps.setString(3,  n(e.getRrn()));
        ps.setString(4,  n(e.getPhone()));
        ps.setInt(5,     e.getWage());
        ps.setString(6,  n(e.getBank()));
        ps.setString(7,  n(e.getAccount()));
        ps.setString(8,  n(e.getAddress()));
        ps.setString(9,  n(e.getContractDate()));
        ps.setString(10, n(e.getMemo()));
        ps.setString(11, e.getEmpNo());
    }
}
