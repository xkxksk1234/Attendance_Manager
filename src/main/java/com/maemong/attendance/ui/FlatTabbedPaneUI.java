package com.maemong.attendance.ui;

import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;

/**
 * 플랫 탭 UI (분리선 추가)
 * - 탭 바 하단에 1px 구분선
 * - 컨텐츠 상단에도 1px 라인
 * - 포커스(점선) 없음
 * - 나머지 그림자/테두리 생략
 */
public class FlatTabbedPaneUI extends BasicTabbedPaneUI {

    // 톤만 살짝 잡아주는 연회색 라인
    private final Color separator = new Color(225, 229, 235);  // 탭-콘텐츠 구분선
    private final Color barBg     = new Color(246, 247, 250);  // 탭 바 배경(없어도 되지만 톤 맞춤)

    @Override
    protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
        // 탭 바 배경 채우기
        int areaH = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        g.setColor(barBg);
        g.fillRect(0, 0, tabPane.getWidth(), areaH);

        // 하단 분리선 (탭과 컨텐츠 사이)
        g.setColor(separator);
        g.drawLine(0, areaH, tabPane.getWidth(), areaH);

        // 기본 탭 도형/텍스트는 필요 없음(헤더 컴포넌트 사용 중)
        // super.paintTabArea(g, tabPlacement, selectedIndex);  // 호출하지 않음
    }

    // 컨텐츠 상단에도 동일한 1px 라인 (플랫하게만 구분)
    @Override
    protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex,
                                             int x, int y, int w, int h) {
        g.setColor(separator);
        g.drawLine(x, y, x + w, y);
    }

    // 나머지 테두리는 생략
    @Override protected void paintContentBorderLeftEdge(Graphics g, int tp, int si, int x, int y, int w, int h) { }
    @Override protected void paintContentBorderBottomEdge(Graphics g, int tp, int si, int x, int y, int w, int h) { }
    @Override protected void paintContentBorderRightEdge(Graphics g, int tp, int si, int x, int y, int w, int h) { }

    // 포커스 점선 제거
    @Override
    protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                       int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) { }
}
