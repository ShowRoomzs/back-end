package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배송 추적 운영값(34 설계서 3-3 · 택배 추적 설계서 4절). 24시간·7일은 §34-13 #17(김화창 확인 대기)이라 코드 상수로 박지 않는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "delivery.tracker")
public class DeliveryTrackerProperties {

    /** false 면 Noop 스텁 — 송장 형식 검증 생략 · 감시 배치 미기동. true 면 스마트택배 어댑터가 붙는다. */
    private boolean enabled = false;

    /**
     * 폴링 일정(KST) — 02~06시를 빼고 2시간 간격, 하루 10회. 스마트택배 FREE 의 「같은 송장 일 최대 조회 10회」를
     * 정확히 다 쓰는 값이다 — 횟수를 늘리려면 요금제를 먼저 올린다.
     */
    private String pollCron = "0 0 0,6-22/2 * * *";

    /** 페이지 크기 — 한 회차에 대상 전량을 이 크기로 나눠 돈다. */
    private int batchSize = 300;

    /** 건 사이 호출 간격(ms). */
    private long callGapMs = 100L;

    /** 집화 확인 필요 — 송장 등록 후 N시간 추적 미조회. */
    private int pickupAlertHours = 24;

    /** 추적 정지 — 마지막 이벤트 후 N일 갱신 없음. */
    private int stallAlertDays = 7;

    private String apiUrl = "https://info.sweettracker.co.kr";

    /** 스마트택배 API 키 — 로그·예외 메시지에 싣지 않는다. */
    private String apiKey;

    /**
     * 송장 형식 검증 — 꺼져 있으면 호출 없이 「검증 생략」이다. 에러 104 가 집화 전 정상 송장에도 오는지
     * 택배사별로 확인하기 전에는 켜지 않는다(정상 등록이 하드 차단된다 · 택배 추적 설계서 3-3).
     */
    private boolean validationEnabled = false;

    /** 이용권 잔여 사용량이 이 비율 밑이면 경고한다. */
    private double usageAlertRatio = 0.2;
}
