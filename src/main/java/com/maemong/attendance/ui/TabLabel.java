package com.maemong.attendance.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** 카드형 탭 헤더(플랫 버전): ● 컬러점 + 제목 + 부제목
 *  - 그림자 없음, 라운드 + 얇은 테두리만
 *  - 포커스 비활성화
 */
public class TabLabel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JLabel dot = new JLabel("●");
    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();

    private boolean selected;

    // 스타일
    private static final int ARC = 14; // 둥글기(원하면 12~18로 조절)
    private static final Color BG_SELECTED   = Color.WHITE;
    private static final Color BG_UNSELECTED = new Color(246, 247, 249);
    private static final Color BORDER_COLOR  = new Color(230, 230, 230);
    private static final Color SUBTEXT_COLOR = new Color(110, 110, 110);

    public TabLabel(String titleText, String subtitleText, Color dotColor) {
        setOpaque(false); // 직접 그리므로
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        setFocusable(false);

        // 점
        dot.setForeground(dotColor);
        dot.setFont(dot.getFont().deriveFont(Font.PLAIN, dot.getFont().getSize2D() + 2f));
        dot.setFocusable(false);

        // 제목/부제
        title.setText(titleText);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setFocusable(false);

        subtitle.setText(subtitleText);
        subtitle.setForeground(SUBTEXT_COLOR);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, subtitle.getFont().getSize2D() - 1f));
        subtitle.setFocusable(false);

        // 레이아웃
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.setFocusable(false);
        left.add(dot);
        left.add(title);

        setLayout(new BorderLayout(6, 2));
        add(left, BorderLayout.NORTH);
        add(subtitle, BorderLayout.SOUTH);

        setSelected(false);
    }

    public void setSelected(boolean selected) { this.selected = selected; repaint(); }
    public boolean isSelected() { return selected; }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth(), h = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 플랫 카드(배경 + 테두리)
        Shape rr = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, ARC, ARC);
        g2.setColor(selected ? BG_SELECTED : BG_UNSELECTED);
        g2.fill(rr);
        g2.setColor(BORDER_COLOR);
        g2.draw(rr);

        g2.dispose();
        super.paintComponent(g);
    }
}
