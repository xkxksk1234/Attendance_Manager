package com.maemong.attendance.config;

import java.nio.file.Path;

/** 현재 선택된 점포 컨텍스트. DB 경로 구성에 사용됨. */
public final class StoreContext {
    private static volatile String storeName = "default"; // 안전망

    private StoreContext(){}

    /** 프로그램 시작 시(또는 전환 시) 반드시 한 번 호출 */
    public static void setStore(String name){
        if (name == null || name.isBlank()) name = "default";
        storeName = name.trim();
    }

    /** 화면 표시용 원래 점포 이름 */
    public static String getStoreName(){
        return storeName;
    }

    /** 파일 시스템용 안전한 점포 디렉터리 이름 */
    public static String getSafeStoreDir(){
        // 영숫자/한글/공백/.-_만 허용, 그 외는 '_' 치환
        String s = storeName.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\uAC00-\\uD7A3 ._\\-]", "_").trim();
        // 공백→언더스코어, 연속 언더스코어 정리
        s = s.replace(' ', '_').replaceAll("_+", "_");
        if (s.isEmpty()) s = "default";
        if (s.length() > 50) s = s.substring(0, 50);
        return s;
    }

    /** 점포별 DB 파일 경로 (상대경로) */
    public static String getDbFilePath(){
        String dir = "./db/" + getSafeStoreDir();
        return Path.of(dir, "attendance.db").toString();
    }
}
