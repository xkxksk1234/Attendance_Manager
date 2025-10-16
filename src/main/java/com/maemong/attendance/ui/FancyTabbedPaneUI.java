package com.maemong.attendance.ui;

import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 미니멀 · 모던 필 스타일 탭 UI (인디케이터 제거, 섀도우 카드) */
public class FancyTabbedPaneUI extends BasicTabbedPaneUI {

    // 팔레트
    private final Color bgBar    = new Color(246,247,250);  // 탭 바 배경
    private final Color barLine  = new Color(232,235,240);  // 바 하단 라인
    private final Color tabBg    = new Color(235,239,244);  // 기본 탭
    private final Color tabHover = new Color(242,245,249);  // 호버 탭
    private final Color tabSel   = Color.white;             // 선택 탭
    private final Color tabEdge  = new Color(214,219,226);  // 선택 테두리
    private final Color text     = new Color(62,70,78);
    private final Color textSel  = new Color(30,36,40);

    private final int arc = 16;   // 둥글기
    private int hoverIndex = -1;

    private final MouseAdapter hover = new MouseAdapter() {
        @Override public void mouseMoved(MouseEvent e){ setHover(tabForCoordinate(tabPane,e.getX(),e.getY())); }
        @Override public void mouseExited(MouseEvent e){ setHover(-1); }
        @Override public void mouseDragged(MouseEvent e){ mouseMoved(e); }
        @Override public void mouseEntered(MouseEvent e){ mouseMoved(e); }
    };

    @Override protected void installDefaults() {
        super.installDefaults();
        tabAreaInsets = new Insets(10,12,10,12);
        contentBorderInsets = new Insets(12,14,14,14);
        tabInsets = new Insets(9,18,9,18);
        tabPane.setOpaque(true);
        tabPane.setBackground(bgBar);
        tabPane.setForeground(text);
        tabPane.setFont(tabPane.getFont().deriveFont(Font.PLAIN, tabPane.getFont().getSize2D()+1f));
    }
    @Override protected void installListeners() {
        super.installListeners();
        tabPane.addMouseMotionListener(hover);
        tabPane.addMouseListener(hover);
    }
    @Override protected void uninstallListeners() {
        tabPane.removeMouseMotionListener(hover);
        tabPane.removeMouseListener(hover);
        super.uninstallListeners();
    }
    private void setHover(int i){
        if (hoverIndex!=i){
            int old = hoverIndex; hoverIndex=i;
            if (old>=0) tabPane.repaint(getTabBounds(tabPane,old));
            if (i>=0)   tabPane.repaint(getTabBounds(tabPane,i));
        }
    }

    /* 바(탭 영역) */
    @Override protected void paintTabArea(Graphics g, int tp, int sel) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int h = calculateTabAreaHeight(tp, runCount, maxTabHeight);
        g2.setColor(bgBar); g2.fillRect(0,0,tabPane.getWidth(),h);
        g2.setColor(barLine); g2.drawLine(0,h,tabPane.getWidth(),h);
        g2.dispose();
        super.paintTabArea(g, tp, sel);
    }

    /* 콘텐츠 박스 – 라운드 카드 */
    @Override protected void paintContentBorder(Graphics g, int tp, int sel) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int top = calculateTabAreaHeight(tp, runCount, maxTabHeight);
        int x = contentBorderInsets.left, y = top+6;
        int w = tabPane.getWidth()-contentBorderInsets.left-contentBorderInsets.right;
        int h = tabPane.getHeight()-y-contentBorderInsets.bottom;
        g2.setColor(Color.white);
        g2.fillRoundRect(x,y,w,h,arc,arc);
        g2.setColor(new Color(225,229,235));
        g2.drawRoundRect(x,y,w,h,arc,arc);
        g2.dispose();
    }

    /* 탭 배경 */
    @Override protected void paintTabBackground(Graphics g, int tp, int idx, int x, int y, int w, int h, boolean sel) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = sel ? tabSel : (idx==hoverIndex ? tabHover : tabBg);
        int yy = y+2, hh = h-6;

        if (sel) { // 아주 옅은 그림자
            g2.setColor(new Color(0,0,0,18));
            g2.fillRoundRect(x, yy+2, w, hh, arc, arc);
        }
        g2.setColor(fill);
        g2.fillRoundRect(x, yy, w, hh, arc, arc);

        if (sel) {
            g2.setColor(tabEdge);
            g2.drawRoundRect(x, yy, w, hh, arc, arc);
        }
        g2.dispose();
    }
    @Override protected void paintTabBorder(Graphics g, int tp, int idx, int x, int y, int w, int h, boolean sel) { /* 배경에서 처리 */ }

    /* 탭 텍스트 */
    @Override
    protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics fm,
                             int tabIndex, String title, Rectangle textRect, boolean isSelected) {
        // Graphics2D로 변환 + 텍스트 안티앨리어싱 힌트 설정
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // LCD 서브픽셀 AA가 가능하면 사용 (불가한 LaF/플랫폼이면 일반 AA로 처리됨)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // 가짜 Bold 대신: 선택 탭은 동일 웨이트(PLAIN) + 살짝 큰 사이즈로 강조
        Font base = font.deriveFont(Font.PLAIN);
        Font use = isSelected ? base.deriveFont(base.getSize2D() + 0.5f) : base;
        g2.setFont(use);

        // 색상
        g2.setColor(isSelected ? textSel : text);

        // 베이스라인은 정수 픽셀에 맞춰 깔끔하게 (임의 -1 보정 제거)
        int tx = textRect.x;
        int ty = textRect.y + g2.getFontMetrics().getAscent();
        g2.drawString(title, tx, ty);

        g2.dispose();
    }

    // 나머지 기본 처리
    @Override protected void paintFocusIndicator(Graphics g, int a, Rectangle[] b, int c, Rectangle d, Rectangle e, boolean f) {}
    @Override protected int calculateTabHeight(int tp, int idx, int fh) { return super.calculateTabHeight(tp, idx, fh)+6; }
    @Override protected int calculateTabWidth(int tp, int idx, FontMetrics fm) { return super.calculateTabWidth(tp, idx, fm)+14; }
}
