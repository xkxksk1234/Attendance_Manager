package com.maemong.attendance.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ItemEvent;

/** 공통 입력 위젯들의 변경을 감지해 onDirty.run()을 호출 */
public final class DirtyTracker {
    private DirtyTracker(){}

    /** 한 컴포넌트 변경 감지 */
    public static void watch(JComponent c, Runnable onDirty){
        if (c == null || onDirty == null) return;

        if (c instanceof JTextField tf){
            if (tf.getDocument() != null) tf.getDocument().addDocumentListener(doc(onDirty));
        } else if (c instanceof JTextArea ta){
            if (ta.getDocument() != null) ta.getDocument().addDocumentListener(doc(onDirty));
        } else if (c instanceof JCheckBox cb){
            cb.addItemListener(e -> {
                if (e.getStateChange()==ItemEvent.SELECTED || e.getStateChange()==ItemEvent.DESELECTED) onDirty.run();
            });
        } else if (c instanceof JComboBox<?> combo){
            combo.addItemListener(e -> { if (e.getStateChange()==ItemEvent.SELECTED) onDirty.run(); });
            if (combo.isEditable() && combo.getEditor() != null && combo.getEditor().getEditorComponent() instanceof JTextField etf) {
                if (etf.getDocument()!=null) etf.getDocument().addDocumentListener(doc(onDirty));
            }
        } else if (c instanceof JSpinner sp){
            sp.addChangeListener(e -> onDirty.run());
        } else if (c instanceof JTable table){
            TableModel m = table.getModel();
            if (m != null) m.addTableModelListener(new TableModelListener() {
                @Override public void tableChanged(TableModelEvent e) { onDirty.run(); }
            });
        }
    }

    /** 컨테이너 아래 모든 입력 위젯을 재귀로 감지(전역 히ュー리스틱 용) */
    public static void watchRecursively(Container root, Runnable onDirty){
        if (root == null || onDirty == null) return;
        if (root instanceof JComponent jc) watch(jc, onDirty);
        for (Component ch : root.getComponents()){
            if (ch instanceof Container cont) watchRecursively(cont, onDirty);
        }
    }

    private static DocumentListener doc(Runnable r){
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { r.run(); }
            @Override public void removeUpdate(DocumentEvent e) { r.run(); }
            @Override public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }
}
