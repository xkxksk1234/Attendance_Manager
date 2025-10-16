package com.maemong.attendance.ui;

import com.maemong.attendance.model.Employee;
import com.maemong.attendance.repository.EmployeeRepository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.NumberFormatter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.awt.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 직원관리 (2025 플랫 UI)
 * - 검색: 사번/이름만. 숫자만 입력 시 사번 "완전 일치"
 * - 등록/수정 분리: 새로 만들기 → 등록, 목록 선택 → 수정(사번 잠금)
 * - 선택 안정화: 필터/리로드 중 선택 억제/검사/지연
 * - 정렬: 헤더 같은 컬럼 3번 클릭 → 기본(정렬 해제)
 * - 툴바 중앙 정렬 + 버튼 크게
 */
public class PanelEmployee extends JPanel implements UnsavedAware {
    private static final long serialVersionUID = 1L;

    /* ===== Repo ===== */
    private final EmployeeRepository repo = new EmployeeRepository();

    /* ===== Toolbar ===== */
    private final JTextField tfSearch = new JTextField(16);
    private final JButton btnNew      = UiKit.primary("새로 만들기");
    private final JButton btnCreate   = UiKit.primary("등록");
    private final JButton btnUpdate   = UiKit.primary("수정");
    private final JButton btnDelete   = UiKit.danger ("삭제");
    private final JButton btnReload   = UiKit.success("새로고침");

    /* ===== Left: Table ===== */
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"사번", "이름", "직급", "시급", "전화"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);

    /* ===== Right: Form ===== */
    private final JTextField tfEmpNo   = UiKit.input(12);
    private final JTextField tfName    = UiKit.input(12);
    private final JTextField tfRank    = UiKit.input(12);
    private final JTextField tfRRN     = UiKit.input(14);
    private final JTextField tfPhone   = UiKit.input(14);
    private final JFormattedTextField tfWage = wageField();
    private final JTextField tfBank    = UiKit.input(12);
    private final JTextField tfAccount = UiKit.input(18);
    private final JTextField tfAddress = UiKit.input(26);
    private final JTextField tfContract= UiKit.input(10);
    private final JTextArea  taMemo    = UiKit.textarea(4);

    /* ===== State ===== */
    private boolean dirty = false;
    private boolean editMode = false;         // false=등록, true=수정
    private String selectedEmpNo = null;
    private boolean suppressSelection = false;

    // 정렬 삼단계 상태 기억
    private int headerColBeforeClick = -1;
    private SortOrder headerOrderBeforeClick = null;

    public PanelEmployee() {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setOpaque(false);

        /* ---------- Toolbar (세로 중앙 정렬 + 버튼 크게) ---------- */
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setOpaque(true);
        bar.setBackground(new Color(249,250,252));
        bar.setBorder(new EmptyBorder(10, 12, 10, 12));
        bar.setPreferredSize(new Dimension(1, 60));

        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftGroup.setOpaque(false);
        leftGroup.add(new JLabel("검색"));
        leftGroup.add(tfSearch);

        JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightGroup.setOpaque(false);
        rightGroup.add(btnNew);
        rightGroup.add(btnCreate);
        rightGroup.add(btnUpdate);
        rightGroup.add(btnDelete);
        rightGroup.add(btnReload);

        // 버튼 크게
        UiKit.makeLarge(btnNew, btnCreate, btnUpdate, btnDelete, btnReload);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0; gc.insets = new Insets(0,0,0,0);
        gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.CENTER;

        gc.gridx = 0; gc.weightx = 0; bar.add(leftGroup, gc);
        gc.gridx = 1; gc.weightx = 1; bar.add(Box.createHorizontalGlue(), gc);
        gc.gridx = 2; gc.weightx = 0; bar.add(rightGroup, gc);

        add(bar, BorderLayout.NORTH);

        /* ---------- Center: Split ---------- */
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, cardList(), cardForm());
        split.setResizeWeight(0.45);
        split.setDividerSize(8);
        add(split, BorderLayout.CENTER);

        /* ---------- Wire ---------- */
        table.setRowSorter(sorter);

        // 검색: 숫자만 → 사번 완전일치, 그 외 → 사번/이름 부분일치
        tfSearch.getDocument().addDocumentListener(new SimpleDoc(() -> {
            String raw = tfSearch.getText().trim();
            if (raw.isEmpty()) {
                sorter.setRowFilter(null);
                SwingUtilities.invokeLater(() -> table.clearSelection());
                return;
            }
            final String q = raw.toLowerCase();
            if (q.matches("^\\d+$")) {
                sorter.setRowFilter(new RowFilter<>() {
                    @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
                        String empNo = safe(e.getStringValue(0)).trim();
                        return empNo.equals(q);
                    }
                });
            } else {
                sorter.setRowFilter(new RowFilter<>() {
                    @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
                        String empNo = safe(e.getStringValue(0)).toLowerCase();
                        String name  = safe(e.getStringValue(1)).toLowerCase();
                        return empNo.contains(q) || name.contains(q);
                    }
                });
            }
            SwingUtilities.invokeLater(() -> table.clearSelection());
        }));

        // 헤더 클릭 삼단 정렬: 오름 → 내림 → 기본(해제)
        table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                int vCol = table.columnAtPoint(e.getPoint());
                if (vCol < 0) return;
                int mCol = table.convertColumnIndexToModel(vCol);
                headerColBeforeClick = mCol;
                headerOrderBeforeClick = currentOrderOf(mCol);
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (headerColBeforeClick < 0) return;
                    int mCol = headerColBeforeClick;
                    SortOrder prev = headerOrderBeforeClick;

                    if (prev == null) {
                        sorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(mCol, SortOrder.ASCENDING)));
                    } else if (prev == SortOrder.ASCENDING) {
                        sorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(mCol, SortOrder.DESCENDING)));
                    } else { // DESC → 기본(해제)
                        sorter.setSortKeys(null);
                    }
                    headerColBeforeClick = -1;
                    headerOrderBeforeClick = null;
                });
            }
        });

        // 버튼 동작
        btnNew.addActionListener(e -> switchToCreateMode());
        btnCreate.addActionListener(e -> create());
        btnUpdate.addActionListener(e -> update());
        btnDelete.addActionListener(e -> deleteSelected());
        btnReload.addActionListener(e -> { reload(); selectRowByEmpNo(selectedEmpNo); });

        // Dirty tracking
        DirtyTracker.watch(tfEmpNo,   () -> dirty = true);
        DirtyTracker.watch(tfName,    () -> dirty = true);
        DirtyTracker.watch(tfRank,    () -> dirty = true);
        DirtyTracker.watch(tfRRN,     () -> dirty = true);
        DirtyTracker.watch(tfPhone,   () -> dirty = true);
        DirtyTracker.watch(tfWage,    () -> dirty = true);
        DirtyTracker.watch(tfBank,    () -> dirty = true);
        DirtyTracker.watch(tfAccount, () -> dirty = true);
        DirtyTracker.watch(tfAddress, () -> dirty = true);
        DirtyTracker.watch(tfContract,() -> dirty = true);
        DirtyTracker.watch(taMemo,    () -> dirty = true);

        // 테이블 선택 → 수정 모드
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onRowSelected(); });

        // 초기 상태
        switchToCreateMode();
        reload();
    }

    /* ===================== UI Builders ===================== */

    private JComponent cardList() {
        JPanel wrap = UiKit.card();
        wrap.setLayout(new BorderLayout(8, 8));
        wrap.add(UiKit.cardTitle("직원 목록"), BorderLayout.NORTH);

        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        wrap.add(sp, BorderLayout.CENTER);

        wrap.add(UiKit.hint("행을 선택하면 우측에 상세가 채워지고 '수정 모드'로 전환됩니다."), BorderLayout.SOUTH);
        return wrap;
    }

    private JComponent cardForm() {
        JPanel wrap = UiKit.card();
        wrap.setLayout(new BorderLayout(8, 8));
        wrap.add(UiKit.cardTitle("직원 상세"), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = UiKit.gc();

        int y=0;
        UiKit.addRow(form, gc, y++, "사번*", tfEmpNo, "이름*", tfName);
        UiKit.addRow(form, gc, y++, "직급", tfRank, "시급", tfWage);
        UiKit.addRow(form, gc, y++, "주민등록번호", tfRRN, "전화번호", tfPhone);
        UiKit.addRow(form, gc, y++, "은행", tfBank, "계좌번호", tfAccount);
        UiKit.addRow(form, gc, y++, "주소", tfAddress, "계약일(YYYY-MM-DD)", tfContract);

        gc.gridx=0; gc.gridy=y; gc.weightx=0; gc.gridwidth=1;
        form.add(UiKit.label("비고"), gc);
        gc.gridx=1; gc.gridy=y; gc.weightx=1; gc.gridwidth=3; gc.fill=GridBagConstraints.HORIZONTAL;
        JScrollPane sp = new JScrollPane(taMemo);
        sp.setBorder(BorderFactory.createLineBorder(UiKit.LINE));
        form.add(sp, gc);

        wrap.add(form, BorderLayout.CENTER);
        wrap.add(UiKit.hint("등록 모드: 사번 입력 가능 · 수정 모드: 사번 잠금"), BorderLayout.SOUTH);

        tfContract.setText(LocalDate.now().toString());
        return wrap;
    }

    private static JFormattedTextField wageField() {
        NumberFormat nf = NumberFormat.getIntegerInstance();
        nf.setGroupingUsed(true);
        NumberFormatter fmt = new NumberFormatter(nf);
        fmt.setAllowsInvalid(false);
        fmt.setMinimum(0);
        fmt.setMaximum(10_000_000);
        JFormattedTextField f = new JFormattedTextField(fmt);
        f.setColumns(10);
        UiKit.styleField(f);
        return f;
    }

    /* ===================== Actions ===================== */

    private void reload() {
        try {
            suppressSelection = true;
            model.setRowCount(0);
            List<Employee> list = repo.listAll();
            for (Employee e : list) {
                model.addRow(new Object[]{
                        e.getEmpNo(), n(e.getName()), n(e.getPosition()),
                        e.getWage(), n(e.getPhone())
                });
            }
            model.fireTableDataChanged();
        } catch (Exception ex) {
            showErr(ex);
        } finally {
            suppressSelection = false;
        }
    }

    private void onRowSelected() {
        if (suppressSelection) return;
        int vrow = table.getSelectedRow();
        if (vrow < 0 || vrow >= table.getRowCount()) return;

        int row = table.convertRowIndexToModel(vrow);
        if (row < 0 || row >= model.getRowCount()) return;

        String empNo = Objects.toString(model.getValueAt(row, 0), null);
        if (empNo == null) return;
        try {
            Employee e = repo.findByEmpNo(empNo);
            if (e != null) {
                fillForm(e);
                switchToEditMode(e.getEmpNo());
            }
        } catch (Exception ex) {
            showErr(ex);
        }
    }

    private void fillForm(Employee e) {
        tfEmpNo.setText(n(e.getEmpNo()));
        tfName.setText(n(e.getName()));
        tfRank.setText(n(e.getPosition()));
        tfRRN.setText(n(e.getRrn()));
        tfPhone.setText(n(e.getPhone()));
        tfWage.setValue(e.getWage());
        tfBank.setText(n(e.getBank()));
        tfAccount.setText(n(e.getAccount()));
        tfAddress.setText(n(e.getAddress()));
        tfContract.setText(n(e.getContractDate()));
        taMemo.setText(n(e.getMemo()));
        dirty = false;
    }

    private Employee toEntity() {
        Employee e = new Employee();
        e.setEmpNo(tfEmpNo.getText().trim());
        e.setName(tfName.getText().trim());
        e.setPosition(tfRank.getText().trim());
        e.setRrn(empty(tfRRN.getText()));
        e.setPhone(empty(tfPhone.getText()));
        e.setWage(((Number) (tfWage.getValue()==null ? 0 : tfWage.getValue())).intValue());
        e.setBank(empty(tfBank.getText()));
        e.setAccount(empty(tfAccount.getText()));
        e.setAddress(empty(tfAddress.getText()));
        e.setContractDate(empty(tfContract.getText()));
        e.setMemo(empty(taMemo.getText()));
        return e;
    }

    /** 등록 */
    private void create() {
        String empNo = tfEmpNo.getText().trim();
        String name  = tfName.getText().trim();
        if (empNo.isEmpty() || name.isEmpty()) { msg("사번과 이름은 필수입니다."); return; }
        try {
            Employee exists = repo.findByEmpNo(empNo);
            if (exists != null) { msg("이미 존재하는 사번입니다: " + empNo); return; }
            repo.insert(toEntity());
            msg("등록 완료");
            dirty = false;
            reload();
            selectRowByEmpNo(empNo);
            switchToEditMode(empNo);
        } catch (Exception ex) {
            showErr(ex);
        }
    }

    /** 수정 */
    private void update() {
        if (!editMode || selectedEmpNo == null) { msg("수정할 직원을 먼저 선택하세요."); return; }
        String name = tfName.getText().trim();
        if (name.isEmpty()) { msg("이름은 필수입니다."); return; }
        try {
            repo.update(toEntity());  // 사번 잠금 상태
            msg("수정 완료");
            dirty = false;
            reload();
            selectRowByEmpNo(selectedEmpNo);
        } catch (Exception ex) {
            showErr(ex);
        }
    }

    private void deleteSelected() {
        int vrow = table.getSelectedRow();
        if (vrow < 0) { msg("삭제할 직원을 선택하세요."); return; }
        int row = table.convertRowIndexToModel(vrow);
        if (row < 0 || row >= model.getRowCount()) return;
        String empNo = Objects.toString(model.getValueAt(row, 0), null);
        if (empNo == null) return;

        int c = JOptionPane.showConfirmDialog(this,
                "사번 " + empNo + " 직원을 삭제합니다.\n(관련 출퇴근 기록은 따로 관리됩니다.)",
                "삭제 확인", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c != JOptionPane.OK_OPTION) return;

        try {
            repo.deleteByEmpNo(empNo);
            msg("삭제되었습니다.");
            clearForm();
            reload();
            switchToCreateMode();
        } catch (Exception ex) {
            showErr(ex);
        }
    }

    /* ===================== Mode helpers ===================== */

    private void switchToCreateMode() {
        editMode = false;
        selectedEmpNo = null;
        tfEmpNo.setEditable(true);
        btnCreate.setEnabled(true);
        btnUpdate.setEnabled(false);
        table.clearSelection();
        clearForm();
    }

    private void switchToEditMode(String empNo) {
        editMode = true;
        selectedEmpNo = empNo;
        tfEmpNo.setEditable(false);   // 사번 잠금
        btnCreate.setEnabled(false);
        btnUpdate.setEnabled(true);
    }

    private void clearForm() {
        tfEmpNo.setText("");
        tfName.setText("");
        tfRank.setText("");
        tfRRN.setText("");
        tfPhone.setText("");
        tfWage.setValue(0);
        tfBank.setText("");
        tfAccount.setText("");
        tfAddress.setText("");
        tfContract.setText(LocalDate.now().toString());
        taMemo.setText("");
        dirty = false;
    }

    private void selectRowByEmpNo(String empNo) {
        SwingUtilities.invokeLater(() -> {
            if (empNo == null) { table.clearSelection(); return; }
            for (int i=0; i<model.getRowCount(); i++) {
                if (empNo.equals(model.getValueAt(i,0))) {
                    int vrow = table.convertRowIndexToView(i);
                    if (vrow >= 0 && vrow < table.getRowCount()) {
                        table.getSelectionModel().setSelectionInterval(vrow, vrow);
                        table.scrollRectToVisible(table.getCellRect(vrow, 0, true));
                    }
                    return;
                }
            }
            table.clearSelection();
        });
    }

    /* ===================== Utils ===================== */

    private SortOrder currentOrderOf(int modelCol) {
        List<? extends RowSorter.SortKey> keys = sorter.getSortKeys();
        if (keys == null || keys.isEmpty()) return null;
        RowSorter.SortKey k = keys.get(0);
        return (k.getColumn() == modelCol) ? k.getSortOrder() : null;
    }

    private static String n(String s){ return s==null? "" : s; }
    private static String empty(String s){ if (s==null) return null; s=s.trim(); return s.isEmpty()? null:s; }
    private static String safe(String s){ return s==null? "" : s; }
    private void msg(String s){ JOptionPane.showMessageDialog(this, s); }
    private void showErr(Exception ex){ ex.printStackTrace(); JOptionPane.showMessageDialog(this, ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE); }

    /* ===== UnsavedAware ===== */
    @Override public boolean hasUnsavedChanges() { return dirty; }
    @Override public void resetUnsaved() { dirty = false; }

    /** 간단 DocListener */
    private static class SimpleDoc implements javax.swing.event.DocumentListener {
        private final Runnable r; SimpleDoc(Runnable r){ this.r=r; }
        @Override public void insertUpdate(DocumentEvent e){ r.run(); }
        @Override public void removeUpdate(DocumentEvent e){ r.run(); }
        @Override public void changedUpdate(DocumentEvent e){ r.run(); }
    }
}
