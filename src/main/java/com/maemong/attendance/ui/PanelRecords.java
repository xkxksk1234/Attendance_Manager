package com.maemong.attendance.ui;

import com.maemong.attendance.model.AttendanceRecord;
import com.maemong.attendance.model.Employee;
import com.maemong.attendance.repository.AttendanceRepository;
import com.maemong.attendance.repository.EmployeeRepository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.Writer;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 조회 탭 (사번 새로고침, 일/월 조회, 삭제, 중복 행 하이라이트, "(사번) 이름" 콤보, CSV 내보내기)
 */
public class PanelRecords extends JPanel implements UnsavedAware {
	private static final long serialVersionUID = 1L;

	/* ===== Repository ===== */
	private final EmployeeRepository employeeRepo = new EmployeeRepository();
	private final AttendanceRepository attendanceRepo = new AttendanceRepository();

	/* ===== Controls (top bar) ===== */
	private final JRadioButton rMonthly = new JRadioButton("월별", true);
	private final JRadioButton rDaily   = new JRadioButton("일별");
	private final JComboBox<String> cbEmp = new JComboBox<>();
	private final JButton btnEmpRefresh = UiKit.success("사번 새로고침");

	private final JComboBox<Integer> cbYear  = new JComboBox<>();
	private final JComboBox<Integer> cbMonth = new JComboBox<>();
	private final JComboBox<Integer> cbDay   = new JComboBox<>();

	private final JButton btnSearch = UiKit.primary("조회");
	private final JButton btnDelete = UiKit.danger("선택 삭제");
	private final JButton btnExport = UiKit.secondary("CSV 내보내기");  // ★ 추가
	private final JTextField tfSearch = UiKit.input(14);
	private final JButton btnClearSearch = UiKit.secondary("지우기");

	// PanelRecords 상단 필드 (없으면 추가)
	private final com.maemong.attendance.service.AttendanceService service =
			new com.maemong.attendance.service.AttendanceService();


	/* ===== Table ===== */
	private final DefaultTableModel model = new DefaultTableModel(
			new Object[]{"사번","이름","날짜","출근","퇴근","메모","ID"}, 0) {
		@Override public boolean isCellEditable(int r, int c) { return false; }
	};
	private final JTable table = new JTable(model);
	private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);

	/* ===== State ===== */
	private boolean dirty = false; // 조회 탭에서는 주로 false 유지
	private final Map<String, Integer> dupCountByKey = new HashMap<>(); // (empNo|date) → count

	public PanelRecords() {
		setLayout(new BorderLayout(12, 12));
		setBorder(new EmptyBorder(12,12,12,12));
		setOpaque(false);

		add(buildToolbar(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);

		// 테이블 설정
		table.setRowSorter(sorter);
		table.setRowHeight(26);
		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);
		// ID 컬럼 숨김
		table.getColumnModel().getColumn(6).setMinWidth(0);
		table.getColumnModel().getColumn(6).setMaxWidth(0);
		table.getColumnModel().getColumn(6).setPreferredWidth(0);
		// 중복 하이라이트 렌더러
		var dupRenderer = new DupRowRenderer();
		for (int i=0;i<6;i++) table.getColumnModel().getColumn(i).setCellRenderer(dupRenderer);

		// 라디오 전환 시 Day enable 토글
		ButtonGroup g = new ButtonGroup();
		g.add(rMonthly); g.add(rDaily);
		rMonthly.addActionListener(e -> updateDayEnable());
		rDaily.addActionListener(e -> updateDayEnable());

		// 액션
		btnEmpRefresh.addActionListener(e -> reloadEmpComboKeepSelection());
		btnSearch.addActionListener(e -> loadRecords());
		btnDelete.addActionListener(e -> deleteSelected());
		btnExport.addActionListener(e -> exportTableToCsv()); // ★ 추가

		// 날짜 콤보 초기화
		initDateCombos();

		// 사번 목록 로딩
		loadEmpItems();

		// 초기 조회(월별)
		loadRecords();

		installSearchFilter(); // ★ 검색 필터
	}

	// PanelRecords 내부 아무 곳에 추가
	private boolean isMonthlyMode() {
		// 네가 월/일 모드를 선택하는 컴포넌트가 라디오가 아니라 콤보/토글이면 여기서 바꿔도 됨.
		try {
			java.lang.reflect.Field f = getClass().getDeclaredField("rbMonthly");
			f.setAccessible(true);
			Object o = f.get(this);
			if (o instanceof javax.swing.JRadioButton) {
				return ((javax.swing.JRadioButton) o).isSelected();
			}
		} catch (Exception ignore) { }
		// 라디오가 없으면 기본을 "월별"로
		return true;
	}

	// "(27236) 홍길동" -> "27236"
	private String selectedEmpNoOrNull() {
		Object sel = cbEmp.getSelectedItem();
		if (sel == null) return null;
		String s = sel.toString().trim();
		if (s.isEmpty() || "전체".equals(s)) return null;

		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\(([^)]+)\\)").matcher(s);
		if (m.find()) return m.group(1).trim();
		return s;
	}

	// yyyy-MM-dd
	private String selectedDate() {
		int y = (int) cbYear.getSelectedItem();
		int m = (int) cbMonth.getSelectedItem();
		int d = (int) cbDay.getSelectedItem();
		return String.format("%04d-%02d-%02d", y, m, d);
	}

	// yyyy-MM
	private String selectedYearMonth() {
		int y = (int) cbYear.getSelectedItem();
		int m = (int) cbMonth.getSelectedItem();
		return String.format("%04d-%02d", y, m);
	}

	private void reloadRecords() {
		try {
			// 0) 테이블 필터(검색 등) 초기화 — 제네릭 오류 안 나게 안전캐스팅
			if (table != null && table.getRowSorter() instanceof javax.swing.table.TableRowSorter) {
				((javax.swing.table.TableRowSorter<?>) table.getRowSorter()).setRowFilter(null);
			}
			if (sorter != null) sorter.setRowFilter(null);

			// 1) 조건 만들기
			final boolean monthly = isMonthlyMode();
			final String empNo = selectedEmpNoOrNull();

			java.util.List<com.maemong.attendance.model.AttendanceRecord> list;
			if (monthly) {
				String ym = selectedYearMonth();          // yyyy-MM (제로패딩)
				list = service.recordsInMonth(ym);
			} else {
				String date = selectedDate();             // yyyy-MM-dd (제로패딩)
				list = service.recordsAt(date);
			}

			// 2) 사번 필터(선택됐을 때만)
			if (empNo != null && !empNo.isBlank()) {
				list.removeIf(r -> r.getEmpNo() == null || !empNo.equals(r.getEmpNo()));
			}

			// 3) 테이블 채우기
			fillTable(list);

		} catch (Exception ex) {
			ex.printStackTrace();
			javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(), "조회 오류",
					javax.swing.JOptionPane.ERROR_MESSAGE);
		}
	}

	// 네가 유지하는 컬럼 순서로 바꾸어 사용
	private void fillTable(java.util.List<com.maemong.attendance.model.AttendanceRecord> list) {
		// model이 널이면 return
		if (model == null) return;
		model.setRowCount(0);
		for (com.maemong.attendance.model.AttendanceRecord r : list) {
			model.addRow(new Object[]{
					r.getId(),
					r.getEmpNo(),
					r.getEmpName(),
					// date는 출근일 기준, outDate가 있으면 옆 칸에 표시하고 싶으면 한 칸 더 추가하세요
					r.getDate(),
					r.getInTime(),
					r.getOutTime(),
					r.getMemo()
			});
		}
		model.fireTableDataChanged();
	}

	/* ===================== UI Builders ===================== */

	private JComponent buildToolbar() {
		JPanel bar = new JPanel(new GridBagLayout());
		bar.setOpaque(true);
		bar.setBackground(new Color(249,250,252));
		bar.setBorder(new EmptyBorder(10, 12, 10, 12));
		bar.setPreferredSize(new Dimension(1, 64));

		// Left group: 모드 + 사번 + 새로고침
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		left.setOpaque(false);
		left.add(new JLabel("모드"));
		left.add(rMonthly);
		left.add(rDaily);

		left.add(new JLabel("사번/이름"));
		cbEmp.setPrototypeDisplayValue("(000000) 홍길동길동"); // 폭 예측치
		left.add(cbEmp);
		left.add(btnEmpRefresh);

		// Right group: 날짜 + 조회/삭제/CSV (+ 검색 UI)
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		right.setOpaque(false);
		right.add(new JLabel("연"));
		right.add(cbYear);
		right.add(new JLabel("월"));
		right.add(cbMonth);
		right.add(new JLabel("일"));
		right.add(cbDay);

		// ★ 검색 UI 추가
		right.add(new JLabel("검색"));
		right.add(tfSearch);
		right.add(btnClearSearch);

		right.add(btnSearch);
		right.add(btnDelete);
		right.add(btnExport);

		// 버튼 큼직하게
		UiKit.makeLarge(btnEmpRefresh, btnSearch, btnDelete, btnExport, btnClearSearch);

		GridBagConstraints gc = new GridBagConstraints();
		gc.gridy = 0; gc.insets = new Insets(0,0,0,0);
		gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.CENTER;

		gc.gridx = 0; gc.weightx = 0; bar.add(left, gc);
		gc.gridx = 1; gc.weightx = 1; bar.add(Box.createHorizontalGlue(), gc);
		gc.gridx = 2; gc.weightx = 0; bar.add(right, gc);

		return bar;
	}

	private JComponent buildCenter() {
		JPanel wrap = UiKit.card();
		wrap.setLayout(new BorderLayout(8,8));
		wrap.add(UiKit.cardTitle("출퇴근 기록"), BorderLayout.NORTH);

		JScrollPane sp = new JScrollPane(table);
		sp.setBorder(BorderFactory.createEmptyBorder());
		wrap.add(sp, BorderLayout.CENTER);

		wrap.add(UiKit.hint("같은 날에 2회 이상 근무한 기록은 연한 노란색으로 표시됩니다."), BorderLayout.SOUTH);
		return wrap;
	}

	/* ===================== Data ===================== */

	private void initDateCombos() {
		cbYear.removeAllItems();
		cbMonth.removeAllItems();
		cbDay.removeAllItems();

		var now = java.time.LocalDate.now();
		int thisYear = now.getYear();

		for (int y = thisYear - 5; y <= thisYear + 1; y++) cbYear.addItem(y);
		for (int m = 1; m <= 12; m++) cbMonth.addItem(m);
		for (int d = 1; d <= 31; d++) cbDay.addItem(d);

		cbYear.setSelectedItem(thisYear);
		cbMonth.setSelectedItem(now.getMonthValue());
		cbDay.setSelectedItem(now.getDayOfMonth());

		updateDayEnable();
	}

	private void updateDayEnable() {
		boolean daily = rDaily.isSelected();
		cbDay.setEnabled(daily);
	}

	/** "(사번) 이름" 형식으로 콤보 채우기 (첫 항목: 전체) */
	private void loadEmpItems() {
		try {
			cbEmp.removeAllItems();
			cbEmp.addItem("전체");
			List<Employee> list = employeeRepo.listAll();
			list.sort(Comparator.comparing(Employee::getEmpNo)); // 사번 오름차순
			for (Employee e : list) {
				String empNo = safe(e.getEmpNo());
				String name  = safe(e.getName());
				cbEmp.addItem("(" + empNo + ") " + name);
			}
			cbEmp.setSelectedIndex(0);
		} catch (Exception ex) {
			showErr(ex);
		}
	}

	/** 새로고침 시 선택 유지 */
	private void reloadEmpComboKeepSelection() {
		String keep = parseEmpNo((String) cbEmp.getSelectedItem());
		loadEmpItems();
		if (keep != null) {
			for (int i=0;i<cbEmp.getItemCount();i++) {
				if (keep.equals(parseEmpNo(cbEmp.getItemAt(i)))) {
					cbEmp.setSelectedIndex(i);
					break;
				}
			}
		}
	}

	private void loadRecords() {
		try {
			model.setRowCount(0);
			dupCountByKey.clear();

			String empNoFilter = parseEmpNo((String) cbEmp.getSelectedItem()); // null이면 전체
			if (rMonthly.isSelected()) {
				int y = (Integer) cbYear.getSelectedItem();
				int m = (Integer) cbMonth.getSelectedItem();
				String ym = String.format("%04d-%02d", y, m);
				List<AttendanceRecord> list = attendanceRepo.findByMonth(ym);

				for (AttendanceRecord r : list) {
					if (empNoFilter != null && !empNoFilter.equals(safe(r.getEmpNo()))) continue;
					addRow(r);
				}
			} else {
				int y = (Integer) cbYear.getSelectedItem();
				int m = (Integer) cbMonth.getSelectedItem();
				int d = (Integer) cbDay.getSelectedItem();
				// 실제 달의 말일을 고려해 day 보정
				int last = YearMonth.of(y, m).lengthOfMonth();
				if (d > last) d = last;
				String ymd = String.format("%04d-%02d-%02d", y, m, d);
				List<AttendanceRecord> list = attendanceRepo.findByDate(ymd);

				for (AttendanceRecord r : list) {
					if (empNoFilter != null && !empNoFilter.equals(safe(r.getEmpNo()))) continue;
					addRow(r);
				}
			}

			// 중복 카운트 계산(사번|날짜)
			calcDup();

			model.fireTableDataChanged();
		} catch (Exception ex) {
			showErr(ex);
		}
	}

	private void addRow(AttendanceRecord r) {
		model.addRow(new Object[]{
				safe(r.getEmpNo()),
				safe(r.getEmpName()),   // 모델에 이름이 없다면 repo에서 join/보강 필요
				safe(r.getDate()),
				safe(r.getInTime()),
				safe(r.getOutTime()),
				safe(r.getMemo()),
				r.getId()               // ID (숨김)
		});
	}

	private void calcDup() {
		dupCountByKey.clear();
		for (int i=0;i<model.getRowCount();i++) {
			String empNo = safe((String) model.getValueAt(i,0));
			String date  = safe((String) model.getValueAt(i,2));
			String key = empNo + "|" + date;
			dupCountByKey.put(key, dupCountByKey.getOrDefault(key, 0)+1);
		}
	}

	private void deleteSelected() {
		int vrow = table.getSelectedRow();
		if (vrow < 0) { msg("삭제할 행을 선택하세요."); return; }
		int row = table.convertRowIndexToModel(vrow);

		String empNo = safe((String) model.getValueAt(row,0));
		String date  = safe((String) model.getValueAt(row,2));
		String inT   = safe((String) model.getValueAt(row,3));
		String outT  = safe((String) model.getValueAt(row,4));
		Object idObj = model.getValueAt(row,6);
		Long id = (idObj instanceof Number) ? ((Number) idObj).longValue() : null;

		int c = JOptionPane.showConfirmDialog(this,
				"다음 기록을 삭제할까요?\n\n사번: " + empNo + "\n날짜: " + date + "\n출근: " + inT + "\n퇴근: " + (outT.isBlank()?"-":outT),
				"삭제 확인", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (c != JOptionPane.OK_OPTION) return;

		try {
			if (id != null) {
				attendanceRepo.deleteById(id); // 레포지토리에 구현되어야 함
			} else {
				attendanceRepo.deleteByKey(empNo, date, inT, outT);
			}
			msg("삭제했습니다.");
			loadRecords();
		} catch (NoSuchMethodError err) {
			showErr(new RuntimeException("삭제 API가 레포지토리에 없습니다. AttendanceRepository에 deleteById(long) 또는 deleteByKey(empNo,date,in,out)을 추가하세요."));
		} catch (Exception ex) {
			showErr(ex);
		}
	}

	/* ===================== CSV Export ===================== */

	private void exportTableToCsv() {
		try {
			if (model.getRowCount() == 0) { msg("내보낼 데이터가 없습니다."); return; }

			JFileChooser fc = new JFileChooser();
			fc.setDialogTitle("CSV 내보내기");
			fc.setSelectedFile(new File("attendance-export.csv"));
			int r = fc.showSaveDialog(this);
			if (r != JFileChooser.APPROVE_OPTION) return;

			File file = fc.getSelectedFile();
			try (Writer w = CsvUtil.newUtf8BomWriter(file)) {
				// 헤더
				int cols = model.getColumnCount();
				String[] header = new String[cols - 1]; // ID는 제외
				for (int c = 0; c < cols - 1; c++) header[c] = model.getColumnName(c);
				CsvUtil.writeLine(w, header);

				// 데이터 (뷰 정렬 반영: sorter 사용)
				for (int vrow = 0; vrow < table.getRowCount(); vrow++) {
					int row = table.convertRowIndexToModel(vrow);
					String[] cells = new String[cols - 1]; // ID 제외
					for (int c = 0; c < cols - 1; c++) {
						Object v = model.getValueAt(row, c);
						cells[c] = (v == null) ? "" : String.valueOf(v);
					}
					CsvUtil.writeLine(w, cells);
				}
			}
			msg("CSV로 내보냈습니다:\n" + file.getAbsolutePath());
		} catch (Exception ex) {
			ex.printStackTrace();
			showErr(new RuntimeException("내보내기 중 오류: " + ex.getMessage()));
		}
	}

	/* ===================== Renderers ===================== */

	/** 같은 사번/날짜 중복 행을 연한 노란색으로 */
	private class DupRowRenderer extends DefaultTableCellRenderer {
		private final Color DUP_BG = new Color(255, 250, 205); // lemon chiffon
		private final Color SEL_BG = table.getSelectionBackground();

		@Override
		public Component getTableCellRendererComponent(JTable tbl, Object val,
		                                               boolean isSelected, boolean hasFocus,
		                                               int row, int column) {
			Component c = super.getTableCellRendererComponent(tbl, val, isSelected, hasFocus, row, column);
			int mRow = tbl.convertRowIndexToModel(row);
			String empNo = safe((String) model.getValueAt(mRow,0));
			String date  = safe((String) model.getValueAt(mRow,2));
			String key = empNo + "|" + date;
			boolean dup = dupCountByKey.getOrDefault(key, 0) > 1;

			if (isSelected) {
				c.setBackground(SEL_BG);
			} else {
				c.setBackground(dup ? DUP_BG : Color.WHITE);
			}
			return c;
		}
	}

	/* ===================== Utils ===================== */

	private static String safe(String s){ return s==null? "" : s; }

	/** "(12345) 홍길동" → "12345", "전체" → null */
	private static String parseEmpNo(String display) {
		if (display == null) return null;
		display = display.trim();
		if (display.equals("전체")) return null;
		Matcher m = Pattern.compile("^\\((\\d+)\\)").matcher(display);
		return m.find() ? m.group(1) : null;
	}

	private void installSearchFilter() {
		// 문서 변경 시마다 필터 적용
		javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
			private void apply() {
				String q = tfSearch.getText().trim();
				if (q.isEmpty()) {
					sorter.setRowFilter(null);
				} else {
					sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(q)));
				}
			}
			@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { apply(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { apply(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { apply(); }
		};
		tfSearch.getDocument().addDocumentListener(dl);

		// 지우기 버튼
		btnClearSearch.addActionListener(e -> {
			tfSearch.setText("");
			sorter.setRowFilter(null);
			tfSearch.requestFocusInWindow();
		});
	}

	private void msg(String s){ JOptionPane.showMessageDialog(this, s); }
	private void showErr(Exception ex){
		ex.printStackTrace();
		JOptionPane.showMessageDialog(this, ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
	}

	/* ===== UnsavedAware ===== */
	@Override public boolean hasUnsavedChanges() { return dirty; }
	@Override public void resetUnsaved() { /* 조회 탭은 주로 변경 없음 */ }
}
