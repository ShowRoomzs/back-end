package showroomz.global.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * 영어 지명을 한글로 매핑하는 유틸리티 클래스
 */
public class LocationNameMapper {

    private static final Map<String, String> COUNTRY_MAP = new HashMap<>();
    private static final Map<String, String> CITY_MAP = new HashMap<>();
    private static final Map<String, String> REVERSE_COUNTRY_MAP = new HashMap<>();
    private static final Map<String, String> REVERSE_CITY_MAP = new HashMap<>();

    static {
        // 국가명 매핑
        COUNTRY_MAP.put("South Korea", "대한민국");
        COUNTRY_MAP.put("United States", "미국");
        COUNTRY_MAP.put("Japan", "일본");
        COUNTRY_MAP.put("China", "중국");
        COUNTRY_MAP.put("United Kingdom", "영국");
        COUNTRY_MAP.put("France", "프랑스");
        COUNTRY_MAP.put("Germany", "독일");
        COUNTRY_MAP.put("Canada", "캐나다");
        COUNTRY_MAP.put("Australia", "호주");
        COUNTRY_MAP.put("Unknown", "알 수 없음");

        // 도시명 매핑
        CITY_MAP.put("Seoul", "서울");
        CITY_MAP.put("Busan", "부산");
        CITY_MAP.put("Incheon", "인천");
        CITY_MAP.put("Daegu", "대구");
        CITY_MAP.put("Daejeon", "대전");
        CITY_MAP.put("Gwangju", "광주");
        CITY_MAP.put("Ulsan", "울산");
        CITY_MAP.put("Suwon", "수원");
        CITY_MAP.put("Yongin-si", "용인시");
        CITY_MAP.put("Seongnam-si", "성남시");
        CITY_MAP.put("Goyang-si", "고양시");
        CITY_MAP.put("Unknown", "알 수 없음");

        // 역방향 매핑 (한글 → 영어)
        COUNTRY_MAP.forEach((eng, kor) -> REVERSE_COUNTRY_MAP.put(kor, eng));
        CITY_MAP.forEach((eng, kor) -> REVERSE_CITY_MAP.put(kor, eng));
    }

    /**
     * 영어 국가명을 한글로 변환
     */
    public static String toKoreanCountry(String englishName) {
        if (englishName == null || englishName.isEmpty()) {
            return "알 수 없음";
        }
        return COUNTRY_MAP.getOrDefault(englishName, englishName);
    }

    /**
     * 영어 도시명을 한글로 변환
     */
    public static String toKoreanCity(String englishName) {
        if (englishName == null || englishName.isEmpty()) {
            return "알 수 없음";
        }
        return CITY_MAP.getOrDefault(englishName, englishName);
    }

    /**
     * 한글 국가명을 영어로 역변환 (DB 검색용)
     */
    public static String toEnglishCountry(String koreanName) {
        if (koreanName == null || koreanName.isEmpty()) {
            return null;
        }
        return REVERSE_COUNTRY_MAP.getOrDefault(koreanName, koreanName);
    }

    /**
     * 한글 도시명을 영어로 역변환 (DB 검색용)
     */
    public static String toEnglishCity(String koreanName) {
        if (koreanName == null || koreanName.isEmpty()) {
            return null;
        }
        return REVERSE_CITY_MAP.getOrDefault(koreanName, koreanName);
    }
}
