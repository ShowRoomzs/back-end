package showroomz.global.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 서버 시각 ↔ 한국 날짜 · 시각.
 *
 * <p>저장 · 비교 · 응답 시각은 서버 벽시계({@code LocalDateTime.now()} — JVM 기본 시간대, 운영은 UTC) 그대로 둔다.
 * <b>한국 달력이 뜻인 계산만</b> 여기를 거친다(58 계획 3-2) — 「오늘」 · 「그날 0시」 · 「그날 23:59:59」 · 영업일 기한 ·
 * 사람이 읽는 번호의 날짜(계약번호 CTR-YYYYMMDD · 공구번호 GB-YYYYMMDD). {@code now.toLocalDate()} 나 {@code date.atTime(23, 59, 59)}
 * 를 그대로 쓰면 운영(UTC)에서 날짜 경계가 KST 09:00 이 되고 기한이 9시간 늦어진다.
 *
 * <p>기준 시간대를 UTC로 박지 않고 JVM 기본 시간대로 해석한다 — 로컬(KST) 실행에서도 같은 순간이 나온다. 그래서 KST JVM 에서는
 * 이 변환이 항등이고, 시간대 회귀는 기본 시간대를 UTC 로 바꾼 테스트로 확인한다.
 */
public final class KstDates {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private KstDates() {
    }

    /** 서버 벽시계 시각의 한국 날짜. */
    public static LocalDate toKstDate(LocalDateTime serverTime) {
        return toKst(serverTime).toLocalDate();
    }

    /** 서버 벽시계 시각 → 한국 벽시계 시각(시 · 일 단위 표시와 버킷 경계). */
    public static LocalDateTime toKst(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).toLocalDateTime();
    }

    /** 한국 벽시계 시각 → 서버 벽시계 시각(저장 · 비교용). */
    public static LocalDateTime fromKst(LocalDateTime kstTime) {
        return kstTime.atZone(KST).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 지금의 한국 날짜 — 「오늘」. */
    public static LocalDate today(LocalDateTime serverNow) {
        return toKstDate(serverNow);
    }

    /** 지금의 한국 날짜 — {@code LocalDate.now()} 대신(운영 JVM 은 UTC 라 KST 00:00 ~ 09:00 에 하루 전이 나온다). */
    public static LocalDate today() {
        return LocalDate.now(KST);
    }

    /** 한국 날짜의 0시 — 서버 시각. 「오늘」 구간의 시작 · 날짜 기간 검색의 경계. */
    public static LocalDateTime startOfDay(LocalDate kstDate) {
        return fromKst(kstDate.atStartOfDay());
    }

    /** 한국 날짜의 끝(23:59:59) — 서버 시각. 「그날까지」 기한. */
    public static LocalDateTime endOfDay(LocalDate kstDate) {
        return fromKst(kstDate.atTime(END_OF_DAY));
    }

    /** 한국 날짜의 그 시각 — 서버 시각(서명 기한 제안 23:55 · D-1 통지 10:00 등). */
    public static LocalDateTime at(LocalDate kstDate, LocalTime kstTime) {
        return fromKst(kstDate.atTime(kstTime));
    }
}
