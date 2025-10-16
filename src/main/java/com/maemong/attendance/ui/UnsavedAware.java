package com.maemong.attendance.ui;

/** 패널이 저장되지 않은 변경사항 존재 여부를 알려주기 위한 인터페이스 */
public interface UnsavedAware {
    /** 저장되지 않은 변경사항이 있으면 true */
    boolean hasUnsavedChanges();

    /** 저장(또는 무시) 후 더티 플래그를 초기화할 때 호출 */
    default void resetUnsaved() {}
}
