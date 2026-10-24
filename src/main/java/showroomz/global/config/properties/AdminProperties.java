package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 어드민 운영 정책값(1017 설계서 6 · 7) — 홈 할 일 · 운영 지표가 같은 기준을 읽는다. 시안 [근거 대기] 항목은 가정값이며 여기서만 바꾼다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /** 1:1 문의 답변 기한(시간) — 어드민 홈 「기한 초과」 · 운영 지표 SLA · 문의 목록 SLA 초과 표시가 같은 값을 쓴다. */
    private int inquirySlaHours = 24;

    /** 브랜드 · 인플루언서 입점 심사 기한(영업일) — [근거 대기] 가정값. */
    private int onboardingSlaBusinessDays = 3;

    /** 변경 요청 검토 기한(시간) — 어드민 변경 요청 화면의 48시간과 같다. */
    private int changeRequestSlaHours = 48;

    /** 세금계산서 발행 마감 몇 영업일 전부터 「기한 임박」으로 올리는가(어드민 홈 rev.4 ②). */
    private int taxInvoiceDueSoonBusinessDays = 5;

    private Metrics metrics = new Metrics();

    @Getter
    @Setter
    public static class Metrics {
        /** 재공구 · 재계약 판정 기간(일) — 시안 ⓐ [근거 대기] 가정값. */
        private int regroupbuyWindowDays = 60;

        /** 플랫폼 수수료 정상 요율 — 베타 0% 동안 「예상」 수익 계산에만 쓴다. */
        private BigDecimal standardFeeRate = new BigDecimal("0.02");
    }
}
