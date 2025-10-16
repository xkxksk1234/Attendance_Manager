package com.maemong.attendance.ui;

import com.maemong.attendance.model.Employee;
import com.maemong.attendance.repository.EmployeeRepository;
import com.maemong.attendance.service.AttendanceService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class PanelAttendance extends JPanel implements UnsavedAware {
    private static final long serialVersionUID = 1L;

    private final AttendanceService svc = new AttendanceService();
    private final EmployeeRepository empRepo = new EmployeeRepository();

    /* ===== 사번 드롭다운: (사번) 이름 ===== */
    private static class EmpItem {
        final String empNo;
        final String name;
        EmpItem(String empNo, String name) { this.empNo = empNo; this.name = name; }
        @Override public String toString() { return "(" + empNo + ") " + (name == null ? "" : name); }
    }
    private final JComboBox<EmpItem> cbEmp = new JComboBox<>();

    /* ===== 날짜: 오늘 체크 + 연/월/일 콤보 ===== */
    private final JCheckBox chkToday = new JCheckBox("오늘", true);
    private final JComboBox<Integer> cbYear  = new JComboBox<>();
    private final JComboBox<Integer> cbMonth = new JComboBox<>();
    private final JComboBox<Integer> cbDay   = new JComboBox<>();

    /* ===== 시각/메모 ===== */
    private final JTextField tfTime  = new JTextField(8);   // HH:mm or HH:mm:ss
    private final JTextField tfMemo  = new JTextField(18);  // 출근시 메모

    /* ===== 버튼 ===== */
    private final JButton btnIn   = new JButton("출근 기록");
    private final JButton btnOut  = new JButton("퇴근 기록");
    private final JButton btnReloadEmp = new JButton("사번 새로고침");

    /* ===== 더티 플래그 ===== */
    private boolean dirty = false;

    public PanelAttendance(){
        setLayout(new BorderLayout(10,10));

        /* ---------- 상단 폼 ---------- */
        var form = new JPanel(new GridBagLayout());
        var gc = new GridBagConstraints();
        gc.insets = new Insets(4,4,4,4);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int y=0;

        // 사번/이름
        cbEmp.setPrototypeDisplayValue(new EmpItem("E000000000", "홍길동")); // 콤보 너비 확보
        addRow(form,gc,y++, new JLabel("사번/이름"), cbEmp);

        // 날짜 영역: 오늘 체크 + 연/월/일 콤보
        var datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        datePanel.add(chkToday);
        datePanel.add(new JLabel("연"));
        datePanel.add(cbYear);
        datePanel.add(new JLabel("월"));
        datePanel.add(cbMonth);
        datePanel.add(new JLabel("일"));
        datePanel.add(cbDay);
        addRow(form,gc,y++, new JLabel("날짜"), datePanel);

        // 시각/메모
        addRow(form,gc,y++, new JLabel("시각(HH:mm[:ss])"), tfTime);
        addRow(form,gc,y++, new JLabel("메모(출근시)"), tfMemo);

        // 버튼들
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnIn.addActionListener(e -> doIn());
        btnOut.addActionListener(e -> doOut());
        btnReloadEmp.addActionListener(e -> loadEmpItems());
        buttons.add(btnIn);
        buttons.add(btnOut);
        buttons.add(btnReloadEmp);

        gc.gridx=1; gc.gridy=y; gc.weightx=1; form.add(buttons,gc);

        add(form, BorderLayout.NORTH);

        /* ---------- 안내 ---------- */
        var tip = """
                사용 예)
                • 오늘 출근/퇴근: [오늘] 체크 후 사번/이름 선택 → [출근 기록] 또는 [퇴근 기록]
                • 지정일 출근/퇴근: [오늘] 체크 해제 → 연/월/일 선택 → 필요하면 시각(옵션) 입력

                - 시각 형식: HH:mm 또는 HH:mm:ss (입력 없으면 현재 시각 사용)
                - 메모는 출근(in) 기록시에만 저장됩니다.
                """;
        add(new JScrollPane(new JTextArea(tip){/**
			 *
			 */
			private static final long serialVersionUID = 1L;

		{
            setEditable(false); setLineWrap(true); setWrapStyleWord(true); setOpaque(false);
        }}), BorderLayout.CENTER);

        /* ---------- 초기값/이벤트 세팅 ---------- */
        initDateCombos();   // 연/월/일 콤보 채우기 + 오늘로 기본 선택
        applyTodayMode();   // '오늘' 체크 상태에 따라 콤보 enable/disable

        chkToday.addActionListener(e -> { applyTodayMode(); dirty = true; });
        cbYear.addActionListener(e -> { refreshDays(); dirty = true; });
        cbMonth.addActionListener(e -> { refreshDays(); dirty = true; });
        cbDay.addActionListener(e -> dirty = true);

        loadEmpItems();     // 사번/이름 목록 로드

        // 입력 변경 → 더티
        DirtyTracker.watch(cbEmp, () -> dirty = true);
        DirtyTracker.watch(tfTime, () -> dirty = true);
        DirtyTracker.watch(tfMemo, () -> dirty = true);
    }

    /* ===================== 내부 로직 ===================== */

    /** 연/월/일 콤보 박스를 초기화하고 오늘 날짜로 맞춘다. */
    private void initDateCombos() {
        cbYear.removeAllItems();
        cbMonth.removeAllItems();
        cbDay.removeAllItems();

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        int currYear = today.getYear();

        for (int y = currYear - 3; y <= currYear + 3; y++) cbYear.addItem(y);
        for (int m = 1; m <= 12; m++) cbMonth.addItem(m);

        cbYear.setSelectedItem(currYear);
        cbMonth.setSelectedItem(today.getMonthValue());

        refreshDays(); // 일 채우기 (선택된 연/월 기준)
        cbDay.setSelectedItem(today.getDayOfMonth());
    }

    /** 선택된 연/월에 따라 일 수를 다시 채운다(윤년 반영). */
    private void refreshDays() {
        Integer y = (Integer) cbYear.getSelectedItem();
        Integer m = (Integer) cbMonth.getSelectedItem();
        if (y == null || m == null) return;

        int prevSelected = cbDay.getSelectedItem() instanceof Integer ? (Integer) cbDay.getSelectedItem() : -1;

        cbDay.removeAllItems();
        int length = YearMonth.of(y, m).lengthOfMonth();
        for (int d = 1; d <= length; d++) cbDay.addItem(d);

        if (prevSelected >= 1 && prevSelected <= length) {
            cbDay.setSelectedItem(prevSelected);
        } else {
            cbDay.setSelectedIndex(0);
        }
    }

    /** '오늘' 체크 상태에 따라 날짜 콤보 enable/disable 및 오늘로 세팅. */
    private void applyTodayMode() {
        boolean today = chkToday.isSelected();
        cbYear.setEnabled(!today);
        cbMonth.setEnabled(!today);
        cbDay.setEnabled(!today);

        if (today) {
            LocalDate d = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            cbYear.setSelectedItem(d.getYear());
            cbMonth.setSelectedItem(d.getMonthValue());
            refreshDays();
            cbDay.setSelectedItem(d.getDayOfMonth());
        }
    }

    /** 현재 선택된 날짜를 yyyy-MM-dd 문자열로 반환. */
    private String getSelectedDate() {
        LocalDate d;
        if (chkToday.isSelected()) {
            d = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        } else {
            Integer y = (Integer) cbYear.getSelectedItem();
            Integer m = (Integer) cbMonth.getSelectedItem();
            Integer day = (Integer) cbDay.getSelectedItem();
            if (y == null || m == null || day == null) throw new IllegalArgumentException("날짜를 선택하세요.");
            d = LocalDate.of(y, m, day);
        }
        return d.toString(); // yyyy-MM-dd
    }

    /** 사번 콤보에 (사번) 이름 목록을 채운다. */
    private void loadEmpItems() {
        try {
            var selected = (EmpItem) cbEmp.getSelectedItem();
            cbEmp.removeAllItems();
            List<Employee> list = empRepo.listAll();  // emp_no 오름차순
            for (var e : list) cbEmp.addItem(new EmpItem(e.getEmpNo(), e.getName()));

            if (selected != null) {
                for (int i = 0; i < cbEmp.getItemCount(); i++) {
                    var it = cbEmp.getItemAt(i);
                    if (it.empNo.equals(selected.empNo)) { cbEmp.setSelectedIndex(i); break; }
                }
            }
            if (cbEmp.getItemCount() > 0 && cbEmp.getSelectedIndex() < 0) cbEmp.setSelectedIndex(0);
        } catch (Exception ex) {
            showErr(ex);
        }
    }

    private String getSelectedEmpNo() {
        var it = (EmpItem) cbEmp.getSelectedItem();
        return (it == null) ? null : it.empNo;
    }

    private void doIn(){
        try{
            var empNo = getSelectedEmpNo();
            if (empNo == null || empNo.isBlank()) { msg("사번을 선택하세요."); return; }

            String date = getSelectedDate();
            String time = emptyToNull(tfTime.getText());
            String memo = emptyToNull(tfMemo.getText());

            svc.clockInAt(empNo, date, time, memo);
            dirty = false; // ✅ 저장 성공 → 더티 해제
            msg("출근 기록 완료");
        }catch(Exception ex){ showErr(ex); }
    }

    private void doOut(){
        try{
            var empNo = getSelectedEmpNo();
            if (empNo == null || empNo.isBlank()) { msg("사번을 선택하세요."); return; }

            String date = getSelectedDate();
            String time = emptyToNull(tfTime.getText());

            svc.clockOutAt(empNo, date, time);
            dirty = false; // ✅ 저장 성공 → 더티 해제
            msg("퇴근 기록 완료");
        }catch(Exception ex){ showErr(ex); }
    }

    /* ===================== 유틸 ===================== */

    private static String emptyToNull(String s){
        if(s==null) return null;
        s = s.trim();
        return s.isEmpty()? null : s;
    }
    private static void addRow(JPanel p, GridBagConstraints gc, int y, JComponent l, JComponent f){
        gc.gridx=0; gc.gridy=y; gc.weightx=0; p.add(l,gc);
        gc.gridx=1; gc.gridy=y; gc.weightx=1; p.add(f,gc);
    }
    private void msg(String s){ JOptionPane.showMessageDialog(this, s); }
    private void showErr(Exception ex){
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
    }

    /* ====== UnsavedAware 구현 ====== */
    @Override public boolean hasUnsavedChanges() { return dirty; }
    @Override public void resetUnsaved() { dirty = false; }
}
