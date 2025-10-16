package com.maemong.attendance.ui;

import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;

/** 탭 포커스(점선) 인디케이터만 끄는 최소 UI 델리게이트 */
public class NoFocusTabbedPaneUI extends BasicTabbedPaneUI {
    @Override
    protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                       int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
        // no-op: 포커스 점선 그리지 않음
    }
}
