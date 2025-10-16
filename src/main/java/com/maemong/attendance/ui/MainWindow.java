package com.maemong.attendance.ui;

import com.maemong.attendance.config.StoreContext;

import javax.swing.*;
import java.awt.*;

/**
 * 메인 윈도우 (카드형 탭 헤더 + 포커스 점선 제거)
 */
public class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JLabel status = new JLabel();
    private final String version;

    private boolean globalDirty = false;

    // (4) 시스템 트레이 아이콘 보관
    private TrayIcon trayIcon;

    public MainWindow(String storeName, String version) {
        super("(" + storeName + ") 근태관리 프로그램 " + version);
        this.version = version;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 840);
        setMinimumSize(new Dimension(1024, 700));
        setLocationRelativeTo(null);

        // (3) 앱 아이콘 적용: 작업표시줄/타이틀바
        try {
            var icons = IconUtil.loadAppIcons(); // /icons/favicon_16/32/48/128.png
            if (!icons.isEmpty()) {
                setIconImages(icons);
            } else {
                var mr = IconUtil.multiResAppIcon();
                if (mr != null) setIconImage(mr);
            }
        } catch (Throwable ignored) {}

        try {
            Font base = UIManager.getFont("Label.font");
            if (base != null) {
                Font uiFont = base.deriveFont(Font.PLAIN, base.getSize2D() + 1.0f);
                UIManager.put("Label.font", uiFont);
                UIManager.put("Button.font", uiFont);
                UIManager.put("TextField.font", uiFont);
                UIManager.put("Table.font", uiFont);
                UIManager.put("TabbedPane.font", uiFont.deriveFont(Font.PLAIN));
            }
        } catch (Exception ignored) {}

        tabs.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        tabs.setOpaque(false);

        setJMenuBar(buildMenuBar());
        rebuildTabs();          // 탭/헤더 구성
        installGlobalWatcher(); // 전역 입력 변경 감지

        status.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(230, 230, 230)));
        updateStatus(StoreContext.getStoreName());

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);

        // (4) 시스템 트레이 아이콘 설치
        installTrayIcon();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu menuFile = new JMenu("파일");

        JMenuItem miSwitch = new JMenuItem("점포 전환...");
        miSwitch.setAccelerator(KeyStroke.getKeyStroke("control shift S"));
        miSwitch.addActionListener(e -> switchStore());

        JMenuItem miExit = new JMenuItem("종료");
        miExit.setAccelerator(KeyStroke.getKeyStroke("alt F4"));
        miExit.addActionListener(e -> {
            removeTrayIcon(); // (4) 종료 전 트레이 제거
            dispose();
        });

        menuFile.add(miSwitch);
        menuFile.addSeparator();
        menuFile.add(miExit);
        mb.add(menuFile);
        return mb;
    }

    /* ===================== 점포 전환 ===================== */

    private void switchStore() {
        if (hasAnyUnsavedChanges() || globalDirty) {
            int c = JOptionPane.showConfirmDialog(
                    this,
                    "저장되지 않은 변경사항이 있습니다.\n점포를 전환하면 변경사항이 사라질 수 있습니다.\n계속 전환하시겠습니까?",
                    "변경사항 확인",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (c != JOptionPane.OK_OPTION) return;
        }

        StoreSelectDialog dlg = new StoreSelectDialog();
        String newStore = dlg.showAndGet();
        if (newStore == null || newStore.isBlank()) return;

        String oldStore = StoreContext.getStoreName();
        if (newStore.equals(oldStore)) return;

        StoreContext.setStore(newStore);
        setTitle("(" + newStore + ") 근태관리 프로그램 " + version);
        updateStatus(newStore);
        rebuildTabs();
        installGlobalWatcher();
        globalDirty = false;

        JOptionPane.showMessageDialog(
                this,
                "점포가 \"" + oldStore + "\" → \"" + newStore + "\"(으)로 전환되었습니다.",
                "전환 완료",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private boolean hasAnyUnsavedChanges() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component comp = tabs.getComponentAt(i);
            if (comp instanceof UnsavedAware ua) {
                try { if (ua.hasUnsavedChanges()) return true; }
                catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private void installGlobalWatcher() {
        DirtyTracker.watchRecursively(tabs, () -> globalDirty = true);
    }

    /* ===================== 탭/헤더 구성 ===================== */

    private void rebuildTabs() {
        tabs.removeAll();

        var pEmployee   = new PanelEmployee();
        var pAttendance = new PanelAttendance();
        var pRecords    = new PanelRecords();

        tabs.add("직원관리", pEmployee);
        tabs.add("출퇴근",   pAttendance);
        tabs.add("조회",     pRecords);

        TabLabel h1 = new TabLabel("직원관리", "등록 · 시급 · 정보", new Color(98,161,255));
        TabLabel h2 = new TabLabel("출퇴근",   "지정일/시각 기록",   new Color(120,207,130));
        TabLabel h3 = new TabLabel("조회",     "일/월별 · 삭제",     new Color(255,191,94));

        tabs.setTabComponentAt(0, wrapHeader(h1));
        tabs.setTabComponentAt(1, wrapHeader(h2));
        tabs.setTabComponentAt(2, wrapHeader(h3));

        // ✅ 플랫 탭 UI 적용: 세로선/그림자/포커스 전부 없음
        try { tabs.setUI(new com.maemong.attendance.ui.FlatTabbedPaneUI()); } catch (Throwable ignored) {}

        tabs.addChangeListener(e -> {
            h1.setSelected(tabs.getSelectedIndex() == 0);
            h2.setSelected(tabs.getSelectedIndex() == 1);
            h3.setSelected(tabs.getSelectedIndex() == 2);
        });
        // 초기 상태 반영
        h1.setSelected(true);

        tabs.revalidate();
        tabs.repaint();
    }

    private JComponent wrapHeader(TabLabel header) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12)); // ← 간격을 살짝 키워 탭 사이 세로선 느낌 제거
        p.add(header, BorderLayout.CENTER);
        p.setFocusable(false);
        header.setFocusable(false);
        return p;
    }

    private void updateStatus(String storeName) {
        status.setText("  현재 점포: " + storeName + "    버전: " + version + "  ");
    }

    /* ===================== (4) 시스템 트레이 ===================== */

    private void installTrayIcon() {
        if (!SystemTray.isSupported()) return;
        try {
            Image trayImg = IconUtil.loadSmallIcon(); // /icons/favicon_16.png
            if (trayImg == null) return;

            PopupMenu menu = new PopupMenu();

            MenuItem miOpen = new MenuItem("열기");
            miOpen.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
                requestFocus();
            });
            menu.add(miOpen);

            MenuItem miExit = new MenuItem("종료");
            miExit.addActionListener(e -> {
                removeTrayIcon();
                dispose();
            });
            menu.add(miExit);

            trayIcon = new TrayIcon(trayImg, "근태관리 (" + StoreContext.getStoreName() + ")", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
                requestFocus();
            });

            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception ignored) {}
    }

    private void removeTrayIcon() {
        if (trayIcon != null) {
            try { SystemTray.getSystemTray().remove(trayIcon); } catch (Exception ignored) {}
            trayIcon = null;
        }
    }

    @Override
    public void dispose() {
        removeTrayIcon(); // 종료 시 트레이 정리
        super.dispose();
    }
}
