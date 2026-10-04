package showroomz.global.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 서버 시각 → 한국 날짜.
 *
 * <p>저장·응답 시각은 서버 벽시계({@code LocalDateTime.now()} — JVM 기본 시간대, 운영은 UTC) 그대로 둔다.
 * 사람이 읽는 번호의 날짜(계약번호 CTR-YYYYMMDD · 공구번호 GB-YYYYMMDD)만 한국 날짜여야 한다 —
 * {@code toLocalDate()}를 그대로 쓰면 KST 00:00~08:59 건이 하루 전 날짜로 찍힌다.
 *
 * <p>기준 시간대를 UTC로 박지 않고 JVM 기본 시간대로 해석한다 — 로컬(KST) 실행에서도 같은 날짜가 나온다.
 */
public final class KstDates {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstDates() {
    }

    /** 서버 벽시계 시각의 한국 날짜. */
    public static LocalDate toKstDate(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(KST).toLocalDate();
    }
}
