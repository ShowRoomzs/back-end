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
    /** 재분배 상한 — M3 「2회 더 실패 시 수동 이체」(임의 · 13절 B-9). */
    private int payoutRetryLimit = 3;

    private BigDecimal pgFeeRate = new BigDecimal("0.03");
    /** 베타 0% — 0원이어도 행을 남긴다. */
    private BigDecimal platformFeeRate = new BigDecimal("0.00");
    /** 정상 요율 — 표시용(파트너 분해 「정상 2%」). */
    private BigDecimal platformFeeNormalRate = new BigDecimal("0.02");
    private BigDecimal rewardVatRate = new BigDecimal("0.10");
    /** 플랫폼 종사자 세율 개정 가능성 — 문구에 고정하지 않는다. */
    private BigDecimal withholdingIncomeRate = new BigDecimal("0.03");
    private BigDecimal withholdingLocalRate = new BigDecimal("0.003");

    /** 브랜드 세금계산서 발행 기한 — 공급일이 속한 달의 다음 달 N일. */
    private int brandInvoiceDueDay = 10;
    /** 인플루언서 세금계산서 첨부 · M5 발행본 상한. */
    private long taxInvoiceAttachmentMaxBytes = 10L * 1024 * 1024;

    // 배치 — 통합 테스트는 끄고 서비스를 직접 부른다(공구 관례).
    private boolean generationSchedulerEnabled = true;
    private boolean autoConfirmSchedulerEnabled = true;
    private boolean payoutSchedulerEnabled = true;
    private boolean payoutResultSchedulerEnabled = true;
    private boolean receiptRetrySchedulerEnabled = true;
    private boolean clawbackSchedulerEnabled = true;

    private Payout payout = new Payout();
    private Platform platform = new Platform();
    private Adjustment adjustment = new Adjustment();

    public enum PayoutMode {
        /** 즉시 PAID · 참조번호 SIM- — 개발 · QA · 통합 테스트. 운영 프로필에서는 기동 실패. */
        SIMULATED,
        /** 포트원 파트너 정산 **테스트 모드**(전 호출 test=true · id 접두 t-) — 개발 서버. 운영 프로필에서는 기동 실패. */
        PG_TEST,
        /** 포트원 파트너 정산 운영 — 44_포트원_파트너정산_연동_BE_설계서.md. */
        PG;

        public boolean isPortOne() {
            return this != SIMULATED;
        }
    }

    @Getter
    @Setter
    public static class Payout {
        private PayoutMode mode = PayoutMode.SIMULATED;
        /** 지시 뒤 N영업일이 지나도 PG 지급 id 가 없으면 「지급 미실행」 알림(포트원 설계서 9-2). */
        private int resultStaleBusinessDays = 1;
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
