package com.maemong.attendance.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

	/** 파일 시스템 안전한 폴더명 생성 */
	public static String getSafeStoreDir() {
		String s = (getStoreName() == null ? "default" : getStoreName());
		s = s.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\uAC00-\\uD7A3 ._\\-]", "_").trim();
		s = s.replace(' ', '_').replaceAll("_+", "_");
		if (s.isEmpty()) s = "default";
		if (s.length() > 50) s = s.substring(0, 50);
		return s;
	}

	/** 점포별 DB 파일 경로 (AppData/Maemong/AttendanceManager/stores/<점포>/db/attendance.db) */
	public static String getDbFilePath() {
		// AppData(Windows) 우선, 없으면 홈 디렉토리 사용
		String appdata = System.getenv("APPDATA"); // 예: C:\Users\USER\AppData\Roaming
		Path base = (appdata != null && !appdata.isBlank())
				? Paths.get(appdata, "Maemong", "AttendanceManager")
				: Paths.get(System.getProperty("user.home"), ".attendance-manager");

		String safe = getSafeStoreDir(); // 이미 클래스에 있을 거예요 (없으면 아래 보조메서드 참고)
		Path db = base.resolve(Paths.get("stores", safe, "db", "attendance.db"));

		// 상위 폴더 생성
		try { Files.createDirectories(db.getParent()); } catch (Exception ignored) {}

		// [1회] 레거시 위치(./db/<점포>/attendance.db)가 있으면 신 위치로 복사
		try {
			Path legacy = Paths.get(".", "db", safe, "attendance.db").toAbsolutePath().normalize();
			if (Files.exists(legacy) && !Files.exists(db)) {
				Files.createDirectories(db.getParent());
				Files.copy(legacy, db);
			}
		} catch (Exception ignored) {}

		return db.toString();
	}

}
