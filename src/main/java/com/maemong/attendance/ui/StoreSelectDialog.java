package com.maemong.attendance.ui;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class StoreSelectDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final JComboBox<String> cbStores = new JComboBox<>();
    private String result = null;

    private static final String PREF_NODE = "com.maemong.attendance";
    private static final String PREF_STORES = "stores_csv";     // 세미콜론(;) 구분 저장
    private static final String PREF_LAST_STORE = "last_store";

    public StoreSelectDialog() {
        setModal(true);
        setTitle("점포 선택");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        cbStores.setEditable(true);

        // 저장된 목록 로드 (없으면 최초 입력 받기)
        List<String> stores = loadStores();
        if (stores.isEmpty()) {
            String first = promptFirstStoreName();
            if (first == null) { // 사용자가 취소
                result = null;
                return;
            }
            stores.add(first);
            saveStores(stores);
            saveLastStore(first);
        }
        for (String s : stores) cbStores.addItem(s);

        // 마지막 선택 복원
        String last = getLastStore();
        if (last != null && !last.isBlank()) cbStores.setSelectedItem(last);

        // ── UI 구성
        var form = new JPanel(new GridBagLayout());
        var gc = new GridBagConstraints();
        gc.insets = new Insets(10,10,10,10);
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx=0; gc.gridy=0; gc.weightx=0; form.add(new JLabel("점포이름:"), gc);
        gc.gridx=1; gc.gridy=0; gc.weightx=1; form.add(cbStores, gc);

        var info = new JLabel("새 점포는 입력 후 [확인], 선택한 점포는 [삭제]로 제거할 수 있습니다. (삭제 시 해당 점포 데이터도 함께 삭제)");
        info.setForeground(new Color(110,110,110));
        gc.gridx=0; gc.gridy=1; gc.gridwidth=2; form.add(info, gc);

        var btnDelete = new JButton("삭제");
        var btnOk     = new JButton("확인");
        var btnCancel = new JButton("취소");

        btnDelete.addActionListener(e -> onDelete());
        btnOk.addActionListener(e -> onOk());
        btnCancel.addActionListener(e -> { result = null; dispose(); });

        // Enter=확인, Esc=취소
        getRootPane().setDefaultButton(btnOk);
        getRootPane().registerKeyboardAction(
                ev -> btnCancel.doClick(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        var btnsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnsLeft.add(btnDelete);

        var btnsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnsRight.add(btnCancel);
        btnsRight.add(btnOk);

        var south = new JPanel(new BorderLayout());
        south.add(btnsLeft, BorderLayout.WEST);
        south.add(btnsRight, BorderLayout.EAST);

        var root = new JPanel(new BorderLayout());
        root.add(form, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        pack();
        setSize(Math.max(getWidth(), 520), Math.max(getHeight(), 190));
        setLocationRelativeTo(null);
    }

    /** 최초 한 번, 목록이 비었을 때 점포명을 입력받는다. (취소 시 null) */
    private String promptFirstStoreName() {
        while (true) {
            String v = JOptionPane.showInputDialog(
                    this,
                    "등록된 점포가 없습니다.\n처음 사용할 점포 이름을 입력하세요.",
                    "점포 이름 설정",
                    JOptionPane.QUESTION_MESSAGE
            );
            if (v == null) return null; // 취소
            v = v.trim();
            if (!v.isEmpty()) return v;
            JOptionPane.showMessageDialog(this, "점포 이름을 입력해 주세요.", "안내", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** [확인]: 새 점포면 추가 저장, 마지막 선택 저장 */
    private void onOk() {
        String v = (String) cbStores.getEditor().getItem();
        if (v == null || v.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "점포이름을 입력하거나 선택해주세요.", "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        v = v.trim();

        boolean exists = false;
        for (int i=0;i<cbStores.getItemCount();i++) {
            if (v.equals(cbStores.getItemAt(i))) { exists = true; break; }
        }
        if (!exists) {
            cbStores.addItem(v);
            List<String> stores = loadStores();
            stores.add(v);
            saveStores(stores);
        }
        saveLastStore(v);

        result = v;
        dispose();
    }

    /** [삭제]: 선택 점포 삭제(데이터 포함) */
    private void onDelete() {
        String sel = (String) cbStores.getSelectedItem();
        if (sel == null || sel.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "삭제할 점포를 선택하세요.", "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        sel = sel.trim();

        List<String> stores = loadStores();
        if (!stores.contains(sel)) {
            JOptionPane.showMessageDialog(this, "해당 점포는 목록에 없습니다: " + sel, "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 1차 확인 (데이터도 함께 삭제 안내)
        int c1 = JOptionPane.showConfirmDialog(
                this,
                "선택한 점포 \"" + sel + "\" 를 삭제합니다.\n" +
                "해당 점포의 직원/출퇴근 데이터를 포함하여 DB 파일이 완전히 삭제됩니다.\n" +
                "계속할까요?",
                "삭제 확인",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (c1 != JOptionPane.OK_OPTION) return;

        // 2차 최종 확인
        int c2 = JOptionPane.showConfirmDialog(
                this,
                "정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.",
                "최종 확인",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (c2 != JOptionPane.OK_OPTION) return;

        // 파일 시스템: ./db/<safeDir>/ 전체 삭제
        Path dir = Paths.get("./db", toSafeDir(sel));
        try {
            deleteDirectoryRecursive(dir);
        } catch (IOException ioex) {
            // 폴더가 없더라도(이미 없는 경우) 무시 가능. 다른 오류는 안내.
            if (Files.exists(dir)) {
                JOptionPane.showMessageDialog(this, "파일 삭제 중 오류: " + ioex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 목록/기억값 갱신
        stores.remove(sel);
        saveStores(stores);

        String last = getLastStore();
        if (sel.equals(last)) {
            saveLastStore(stores.isEmpty() ? "" : stores.get(0));
        }

        // 콤보 갱신
        cbStores.removeAllItems();
        for (String s : stores) cbStores.addItem(s);

        if (stores.isEmpty()) {
            // 모두 삭제됨 → 새 점포 입력 유도
            String first = promptFirstStoreName();
            if (first == null) {
                JOptionPane.showMessageDialog(this, "점포가 하나 이상 필요합니다. 창을 닫습니다.", "안내", JOptionPane.INFORMATION_MESSAGE);
                result = null;
                dispose();
                return;
            }
            stores.add(first);
            saveStores(stores);
            saveLastStore(first);
            cbStores.addItem(first);
            cbStores.setSelectedItem(first);
        } else {
            cbStores.setSelectedIndex(0);
        }

        JOptionPane.showMessageDialog(this, "삭제되었습니다.");
    }

    /** 닫힌 후 선택된 점포이름을 반환. 취소 시 null */
    public String showAndGet() {
        if (result == null && !isDisplayable()) return null;
        setVisible(true);
        return result;
    }

    /* ===================== 저장/로드 ===================== */

    private static List<String> loadStores() {
        Preferences p = Preferences.userRoot().node(PREF_NODE);
        String csv = p.get(PREF_STORES, "").trim();
        List<String> list = new ArrayList<>();
        if (!csv.isEmpty()) {
            for (String s : csv.split(";")) {
                s = s.trim();
                if (!s.isEmpty()) list.add(s);
            }
        }
        return list;
    }

    private static void saveStores(List<String> stores) {
        Preferences p = Preferences.userRoot().node(PREF_NODE);
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<stores.size();i++) {
            if (i>0) sb.append(';');
            sb.append(stores.get(i).replace(";", " "));
        }
        p.put(PREF_STORES, sb.toString());
    }

    private static String getLastStore() {
        Preferences p = Preferences.userRoot().node(PREF_NODE);
        String last = p.get(PREF_LAST_STORE, "");
        return last.isBlank() ? null : last;
    }

    private static void saveLastStore(String store) {
        Preferences p = Preferences.userRoot().node(PREF_NODE);
        p.put(PREF_LAST_STORE, store == null ? "" : store);
    }

    /* ===================== 파일 시스템 유틸 ===================== */

    /** 점포명을 파일 시스템에 안전한 폴더명으로 변환 (StoreContext와 동일 규칙) */
    private static String toSafeDir(String name){
        if (name == null) return "default";
        String s = name.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\uAC00-\\uD7A3 ._\\-]", "_").trim();
        s = s.replace(' ', '_').replaceAll("_+", "_");
        if (s.isEmpty()) s = "default";
        if (s.length() > 50) s = s.substring(0, 50);
        return s;
    }

    /** 디렉터리를 재귀적으로 삭제 (존재하지 않으면 조용히 무시) */
    private static void deleteDirectoryRecursive(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        // 파일부터 지우고 폴더 지우기
        Files.walk(dir)
                .sorted((a,b) -> b.getNameCount() - a.getNameCount()) // 하위 경로 먼저
                .forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
    }
}
