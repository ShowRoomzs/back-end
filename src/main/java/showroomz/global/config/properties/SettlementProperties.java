package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 정산 운영값(44 어드민 설계서 9-1 · 이슈 스레드 설계서 1-8) — 전부 {@code [근거 대기]}라 설정값이다. 네 설계서가 이 클래스
 * 하나를 쓴다(공통 결정 #21). 요율은 생성 시점에 정산 행에 스냅샷되므로 바꿔도 지난 정산은 그때 요율로 읽힌다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "settlement")
public class SettlementProperties {

    /** 정산 확인 기간 — 생성 다음 영업일부터 N영업일째 23:59:59(§41-3). */
    private int reviewBusinessDays = 3;
    /** 지급 예정일 — 확정일 + N영업일(§41-5 · [근거 대기 · PG 계약]). */
    private int payoutBusinessDays = 3;
    /** 지급 배치 시각(영업일 · KST 시). 크론은 {@code ${settlement.payout-hour}}로 읽는다. */
    private int payoutHour = 10;
    /**
     * 마감 전 틱 시각(영업일 · KST 시 · 60 이니시스 전환 계획서 D-2′) — 늦게 풀린 행 지시 · 수렴 · 잔액 재검사. 이니시스 익일지급 수정 마감(15시) 전이어야
     * 한다. 크론은 {@code ${settlement.payout-cutoff-hour}}로 읽는다.
     */
    private int payoutCutoffHour = 14;
    /** 재분배 상한 — M3 「2회 더 실패 시 수동 이체」(임의 · 13절 B-9). */
    private int payoutRetryLimit = 3;

    private BigDecimal pgFeeRate = new BigDecimal("0.03");
    /** 베타 0% — 0원이어도 행을 남긴다. */
    private BigDecimal platformFeeRate = new BigDecimal("0.00");
    /** 정상 요율 — 표시용(파트너 분해 「정상 2%」). */
    private BigDecimal platformFeeNormalRate = new BigDecimal("0.02");
    private BigDecimal rewardVatRate = new BigDecimal("0.10");

    /**
     * 리워드 = 부가세 포함 금액(대표 결정 2026-11-10 · 세무 확인 필요). true 면 브랜드의 리워드 부가세 별도 부담이 없고, 사업자 인플루언서의
     * 부가세는 리워드 안에서 나뉜다(공급가 = 리워드 ÷ (1 + 부가세율)). 생성 때 정산 행에 스냅샷한다.
     */
    private boolean rewardVatInclusive = true;
    /** 플랫폼 종사자 세율 개정 가능성 — 문구에 고정하지 않는다. */
    private BigDecimal withholdingIncomeRate = new BigDecimal("0.03");
    private BigDecimal withholdingLocalRate = new BigDecimal("0.003");

    /** 브랜드 세금계산서 발행 기한 — 공급일이 속한 달의 다음 달 N일. */
    private int brandInvoiceDueDay = 10;
    /** 인플루언서 세금계산서 첨부 · M5 발행본 상한. */
    private long taxInvoiceAttachmentMaxBytes = 10L * 1024 * 1024;

    // 배치 — 통합 테스트는 끄고 서비스를 직접 부른다(공구 관례).
    /**
     * 런칭 게이트 — 실제 돈이 오가는 운영 서버에서만 true. 켜진 {@code prod} 는 SHOWROOMZ 실제 사업자 정보 · {@code PG} 지급 모드를
     * 강제한다. 지금 {@code prod} 프로필로 도는 서버는 개발 단계라 기본 false(시뮬레이터 지급 · 기동 경고만).
     */
    private boolean launchGateEnabled = false;

    private boolean generationSchedulerEnabled = true;
    private boolean autoConfirmSchedulerEnabled = true;
    private boolean payoutSchedulerEnabled = true;
    private boolean payoutResultSchedulerEnabled = true;
    private boolean payoutCutoffSchedulerEnabled = true;
    private boolean receiptRetrySchedulerEnabled = true;
    private boolean clawbackSchedulerEnabled = true;

    private Payout payout = new Payout();
    private Platform platform = new Platform();
    private Adjustment adjustment = new Adjustment();
    private Clawback clawback = new Clawback();

    public enum PayoutMode {
        /** 즉시 PAID · 참조번호 SIM- — 개발 · QA · 통합 테스트. 런칭 게이트 켜진 운영 프로필에서는 기동 실패. */
        SIMULATED,
        /**
         * PG 테스트 환경 — 토스: 테스트 키(test_sk_ · 참조 id 접두 t-) · 이니시스: 계약담당자가 준 스테이징(base URL · MID · 키 별도 · idMall 접두 t).
         * 개발 서버. 런칭 게이트 켜진 운영 프로필에서는 기동 실패.
         */
        PG_TEST,
        /** PG 운영 — 토스(live_sk_ · 44 토스 설계서) / 이니시스(apiwi.inicis.com · 60 이니시스 전환 계획서). */
        PG;

        /** 바깥 PG 를 부르는가 — 결과 폴링 · 잔액 선검사 · 셀러 연결 기록은 이 모드들만. */
        public boolean isExternal() {
            return this != SIMULATED;
        }

        public boolean isTest() {
            return this == PG_TEST;
        }
    }

    /**
     * 지급 PG(60 이니시스 전환 계획서 D-14) — 전환 기간에만 둔다. 기본 {@code TOSS} — 토스 키로 {@code PG_TEST} 를 돌던 서버가 이 배포로 기동이
     * 깨지지 않게. 이니시스가 스테이징을 통과하면 토스 어댑터와 함께 이 속성을 지운다(이니시스 고정).
     */
    public enum PayoutProvider {
        TOSS,
        INICIS
    }

    @Getter
    @Setter
    public static class Payout {
        private PayoutMode mode = PayoutMode.SIMULATED;
        /** {@code PG_TEST · PG} 의 PG — 전환 기간만(D-14). {@code SIMULATED} 는 보지 않는다. */
        private PayoutProvider provider = PayoutProvider.TOSS;
        /**
         * 처리 지연 알림 — 정산별 경로(토스): 지시 뒤 N영업일 · 묶음 경로(이니시스): <b>지급일</b> 뒤 N영업일이 지나도 결과가 없으면 「PG 처리 지연」
         * (토스 설계서 5-4 · 이니시스 D-8 — 지시가 하루 당겨져 지시 기준이면 결과 전에 매일 거짓 알림이 난다).
         */
        private int resultStaleBusinessDays = 1;
        /**
         * 7일 누적 기준(1천만 원) — 토스: PARTIALLY_APPROVED 셀러의 주당 한도 선보류(5-2 ①) · 이니시스: AML 고객확인 기준 안내(D-6 · aml-precheck).
         */
        private long weeklyLimitAmount = 10_000_000L;
    }

    /** SHOWROOMZ 사업자 정보 — 인플루언서 세금계산서의 공급받는자 · 원천징수영수증 발행자(13절 신규 #6). */
    @Getter
    @Setter
    public static class Platform {
        private String businessName;
        private String representative;
        private String registrationNumber;
        private String address;
        private String taxEmail;
    }

    /** 지급 전 환불(1021 결정 노트 기-13 【잠정】 · 설계서 6-3). */
    @Getter
    @Setter
    public static class Clawback {
        /** 이 상태의 정산은 클로백 대신 재계산 · 확인 기간 재시작(14절 Q18 — 기본 확인 기간만). 비우면 언제나 클로백. */
        private java.util.Set<showroomz.domain.settlement.type.SettlementStatus> recalculateStatuses =
                java.util.EnumSet.of(showroomz.domain.settlement.type.SettlementStatus.REVIEWING);
    }

    /** 정산 조정 협의(이슈 스레드 설계서 1-8). */
    @Getter
    @Setter
    public static class Adjustment {
        /** 합의 기한 — 개설 + N영업일 · 연장 없음(§42-3). */
        private int deadlineBusinessDays = 10;
        /** 마감 시각 — 어드민 23:59 · 파트너 · 스튜디오 18:00 충돌(§46 B-4)을 설정값으로 흡수. */
        private LocalTime deadlineTime = LocalTime.of(23, 59, 59);
        /** D-1 통지 — 마감일의 N영업일 전(잠정 · 영업일 기준). */
        private int noticeBusinessDaysBefore = 1;
        private LocalTime noticeTime = LocalTime.of(10, 0);
        private int reasonMaxLength = 1000;
        private boolean schedulerEnabled = true;
        private int batchSize = 200;
    }
}
