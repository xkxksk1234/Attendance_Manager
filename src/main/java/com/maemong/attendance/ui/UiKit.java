package com.maemong.attendance.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** 앱 공용 UI 유틸 (2025 플랫 스타일) */
public final class UiKit {
	private UiKit(){}

	/* 색 팔레트 */
	public static final Color LINE = new Color(230,230,230);
	public static final Color HINT = new Color(120,120,120);

	public static final Color PRIMARY       = new Color(48,108,246);
	public static final Color PRIMARY_HOVER = new Color(38,96,230);
	public static final Color PRIMARY_PRESS = new Color(30,82,204);

	public static final Color DANGER        = new Color(225,76,70);
	public static final Color DANGER_HOVER  = new Color(210,66,60);
	public static final Color DANGER_PRESS  = new Color(190,50,46);

	public static final Color SUCCESS       = new Color(34,180,99);
	public static final Color SUCCESS_HOVER = new Color(30,165,90);
	public static final Color SUCCESS_PRESS = new Color(26,150,80);

	public static final Color DISABLED_BG   = new Color(235,237,240);
	public static final Color DISABLED_FG   = new Color(155,160,165);

	/* ★ 세컨더리 톤(라이트 버튼용) */
	public static final Color SECONDARY_BG     = new Color(245,246,248);
	public static final Color SECONDARY_HOVER  = new Color(235,238,242);
	public static final Color SECONDARY_PRESS  = new Color(225,229,235);
	public static final Color SECONDARY_FG     = new Color(33,37,41);

	/* ---- 카드/타이틀/라벨 ---- */
	public static JPanel card(){
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(LINE),
				new EmptyBorder(12, 12, 12, 12)
		));
		return p;
	}
	public static JLabel cardTitle(String t){
		JLabel l = new JLabel(t);
		l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D()+1.5f));
		return l;
	}
	public static JLabel hint(String t){
		JLabel l = new JLabel(t);
		l.setForeground(HINT);
		l.setFont(l.getFont().deriveFont(l.getFont().getSize2D()-0.5f));
		return l;
	}
	public static JLabel label(String t){
		JLabel l = new JLabel(t);
		l.setForeground(new Color(60,60,60));
		return l;
	}

	/* ---- 입력 ---- */
	public static JTextField input(int cols){
		JTextField f = new JTextField(cols);
		styleField(f);
		return f;
	}
	public static JTextArea textarea(int rows){
		JTextArea a = new JTextArea(rows, 10);
		a.setLineWrap(true);
		a.setWrapStyleWord(true);
		a.setBorder(new EmptyBorder(6,6,6,6));
		return a;
	}
	public static void styleField(JComponent c){
		c.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(LINE),
				new EmptyBorder(6,8,6,8)
		));
	}

	/* ---- GridBag 편의 ---- */
	public static GridBagConstraints gc(){
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(6,6,6,6);
		gc.fill = GridBagConstraints.HORIZONTAL;
		return gc;
	}
	public static void addRow(JPanel form, GridBagConstraints gc, int y,
	                          String l1, JComponent f1, String l2, JComponent f2){
		gc.gridx=0; gc.gridy=y; gc.weightx=0; gc.gridwidth=1; form.add(label(l1), gc);
		gc.gridx=1; gc.gridy=y; gc.weightx=1; gc.gridwidth=1; form.add(f1, gc);
		gc.gridx=2; gc.gridy=y; gc.weightx=0; gc.gridwidth=1; form.add(label(l2), gc);
		gc.gridx=3; gc.gridy=y; gc.weightx=1; gc.gridwidth=1; form.add(f2, gc);
	}

	/* ---- 공통 솔리드 버튼(Primary/Danger/Success) ---- */
	private static JButton solidButton(String text, Color base, Color hover, Color press){
		JButton b = new JButton(text);
		b.setFont(b.getFont().deriveFont(Font.BOLD));
		b.setForeground(Color.WHITE);
		b.setBackground(base);
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setBorderPainted(false);
		b.setFocusPainted(false);
		b.setBorder(new EmptyBorder(8,16,8,16));

		b.getModel().addChangeListener(e -> {
			ButtonModel m = b.getModel();
			if (!b.isEnabled()) {
				b.setBackground(DISABLED_BG);
				b.setForeground(DISABLED_FG);
			} else if (m.isPressed()) {
				b.setBackground(press);
				b.setForeground(Color.WHITE);
			} else if (m.isRollover()) {
				b.setBackground(hover);
				b.setForeground(Color.WHITE);
			} else {
				b.setBackground(base);
				b.setForeground(Color.WHITE);
			}
		});
		return b;
	}

	/* ---- 라이트(세컨더리) 버튼 ---- */
	private static JButton lightButton(String text, Color base, Color hover, Color press){
		JButton b = new JButton(text);
		b.setFont(b.getFont().deriveFont(Font.PLAIN)); // 세컨더리는 굵기 낮춤
		b.setForeground(SECONDARY_FG);
		b.setBackground(base);
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setBorderPainted(true);
		b.setFocusPainted(false);
		b.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(LINE),
				new EmptyBorder(8,16,8,16)
		));

		b.getModel().addChangeListener(e -> {
			ButtonModel m = b.getModel();
			if (!b.isEnabled()) {
				b.setBackground(DISABLED_BG);
				b.setForeground(DISABLED_FG);
			} else if (m.isPressed()) {
				b.setBackground(press);
				b.setForeground(SECONDARY_FG);
			} else if (m.isRollover()) {
				b.setBackground(hover);
				b.setForeground(SECONDARY_FG);
			} else {
				b.setBackground(base);
				b.setForeground(SECONDARY_FG);
			}
		});
		return b;
	}

	// 버튼을 살짝 크게: 폰트 + 패딩만 키움 (전역 스타일은 건드리지 않음)
	public static void makeLarge(JButton... buttons){
		for (JButton b : buttons){
			b.setFont(b.getFont().deriveFont(b.getFont().getSize2D() + 1.0f)); // +1pt
			b.setBorder(new EmptyBorder(10, 18, 10, 18)); // 패딩 업
			// 높이 최소값 보호(플랫폼별 라플 차이 방지)
			Dimension sz = b.getPreferredSize();
			if (sz.height < 38) sz.height = 38;
			b.setPreferredSize(sz);
		}
	}

	public static JButton primary(String text){ return solidButton(text, PRIMARY, PRIMARY_HOVER, PRIMARY_PRESS); }
	public static JButton danger (String text){ return solidButton(text, DANGER,  DANGER_HOVER,  DANGER_PRESS ); }
	public static JButton success(String text){ return solidButton(text, SUCCESS, SUCCESS_HOVER, SUCCESS_PRESS); }

	/** ★ 세컨더리(라이트) 버튼: CSV 내보내기 같은 서브 액션에 적합 */
	public static JButton secondary(String text){ return lightButton(text, SECONDARY_BG, SECONDARY_HOVER, SECONDARY_PRESS); }
}
