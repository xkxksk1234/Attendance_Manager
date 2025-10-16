package com.maemong.attendance;

import com.maemong.attendance.config.StoreContext;
import com.maemong.attendance.ui.MainWindow;
import com.maemong.attendance.ui.StoreSelectDialog;

import javax.swing.*;
import java.util.Optional;

public class App {
    public static void main(String[] args) {
        // 전역 텍스트 안티앨리어싱 (가독성 개선)
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");

        // 예상 못한 예외를 다이얼로그로 보여주기 (선택이지만 추천)
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                    null,
                    ex.getMessage(),
                    "예기치 못한 오류",
                    JOptionPane.ERROR_MESSAGE
                )
            );
        });

        SwingUtilities.invokeLater(() -> {
            // 시스템 Look & Feel 적용
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 버전: JAR Manifest의 Implementation-Version, 없으면 기본값
            String version = Optional.ofNullable(App.class.getPackage().getImplementationVersion())
                                     .orElse("0.0.1-SNAPSHOT");

            // 점포 선택
            StoreSelectDialog dlg = new StoreSelectDialog();
            String storeName = dlg.showAndGet();
            if (storeName == null || storeName.isBlank()) {
                return; // 사용자 취소
            }

            // 점포 컨텍스트 지정: 이후 DB 연결이 점포별 파일로 분기됨
            StoreContext.setStore(storeName);

            // 메인 윈도우 실행
            new MainWindow(storeName, version).setVisible(true);
        });
    }
}
