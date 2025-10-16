package com.maemong.attendance.ui;

import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 둥근 필 스타일의 모던 탭 UI (rollover 자체 추적) */
public class ModernTabbedPaneUI extends BasicTabbedPaneUI {

    // 팔레트
    private final Color bg      = new Color(245,246,248);
    private final Color tabBg   = new Color(235,238,242);
    private final Color selBg   = new Color(255,255,255);
    private final Color selEdge = new Color(210,214,220);
    private final Color text    = new Color(70,78,86);
    private final Color textSel = new Color(30,36,40);
    private final Color hoverBg = new Color(245,247,250);
    private final Color barLine = new Color(225,229,235);
    private final int arc = 16;

    // 자체 롤오버 인덱스
    private int hoverIndex = -1;
    private final MouseAdapter hoverTracker = new MouseAdapter() {
        @Override public void mouseMoved(MouseEvent e)  { updateHover(e); }
        @Override public void mouseExited(MouseEvent e) { setHover(-1); }
        @Override public void mouseEntered(MouseEvent e){ updateHover(e); }
        @Override public void mouseDragged(MouseEvent e){ updateHover(e); }
    };

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabAreaInsets = new Insets(8, 8, 8, 8);
        contentBorderInsets = new Insets(10, 12, 12, 12);
        tabInsets = new Insets(8, 16, 8, 16);
        tabPane.setOpaque(true);
        tabPane.setBackground(bg);
        tabPane.setForeground(text);
        tabPane.setFont(tabPane.getFont().deriveFont(Font.PLAIN, tabPane.getFont().getSize2D() + 1f));
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        tabPane.addMouseMotionListener(hoverTracker);
        tabPane.addMouseListener(hoverTracker);
    }

    @Override
    protected void uninstallListeners() {
        tabPane.removeMouseMotionListener(hoverTracker);
        tabPane.removeMouseListener(hoverTracker);
        super.uninstallListeners();
    }

    private void updateHover(MouseEvent e) {
        int idx = tabForCoordinate(tabPane, e.getX(), e.getY());
        setHover(idx);
    }
    private void setHover(int idx){
        if (hoverIndex != idx) {
            int old = hoverIndex;
            hoverIndex = idx;
            if (old >= 0) repaintTab(old);
            if (hoverIndex >= 0) repaintTab(hoverIndex);
        }
    }
    private void repaintTab(int index){
        Rectangle r = getTabBounds(tabPane, index);
        if (r != null) tabPane.repaint(r);
    }

    @Override
    protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
        Graphics2D g2 = (Graphics2D) g.create();
        int h = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        g2.setColor(bg);
        g2.fillRect(0, 0, tabPane.getWidth(), h);
        g2.setColor(barLine);
        g2.drawLine(0, h, tabPane.getWidth(), h);
        g2.dispose();
        super.paintTabArea(g, tabPlacement, selectedIndex);
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean isHover = (tabIndex == hoverIndex);
        Color fill = isSelected ? selBg : (isHover ? hoverBg : tabBg);

        int yy = y + 2;
        int hh = h - 4;

        g2.setColor(fill);
        g2.fillRoundRect(x, yy, w, hh, arc, arc);

        if (isSelected) {
            g2.setColor(selEdge);
            g2.drawRoundRect(x, yy, w, hh, arc, arc);
        }
        g2.dispose();
    }

    @Override
    protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                             int tabIndex, String title, Rectangle textRect, boolean isSelected) {
        g.setFont(font);
        g.setColor(isSelected ? textSel : text);
        int ty = textRect.y + metrics.getAscent() - (isSelected ? 1 : 0);
        g.drawString(title, textRect.x, ty);
    }

    @Override protected void paintFocusIndicator(Graphics g, int tp, Rectangle[] r, int i, Rectangle ir, Rectangle tr, boolean s) { }
    @Override protected void paintTabBorder(Graphics g, int tp, int i, int x, int y, int w, int h, boolean s) { }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int top = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        int x = contentBorderInsets.left;
        int y = top + 4;
        int w = tabPane.getWidth() - contentBorderInsets.left - contentBorderInsets.right;
        int h = tabPane.getHeight() - y - contentBorderInsets.bottom;

        g2.setColor(selBg);
        g2.fillRoundRect(x, y, w, h, arc, arc);
        g2.setColor(selEdge);
        g2.drawRoundRect(x, y, w, h, arc, arc);

        g2.dispose();
    }

    @Override protected int calculateTabHeight(int tp, int idx, int fh) {
        return super.calculateTabHeight(tp, idx, fh) + 6;
    }
    @Override protected int calculateTabWidth(int tp, int idx, FontMetrics fm) {
        return super.calculateTabWidth(tp, idx, fm) + 12;
    }
}
