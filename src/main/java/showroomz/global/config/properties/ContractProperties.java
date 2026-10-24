package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

/**
 * 계약 운영값 — 1021 설계서 1-7 · 1-9 · 14-A(결정 노트 기-07 · 기-28). 【잠정】 · [정책값 미정] 은 설정값으로 둔다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "contract")
public class ContractProperties {

    /** 서명 기한 제안값 — 공구 시작 N일 전(기-07 기본 3일). 운영자가 확인한 뒤 모두싸인에 같은 값을 넣는다. */
    private int signatureDeadlineDaysBeforeStart = 3;
    /** 서명 기한 제안 시각(KST) — 시안 C1 「공구 시작 3일 전 23:55」. */
    private LocalTime signatureDeadlineSuggestTime = LocalTime.of(23, 55);
    /** 고정 지급비 — 지급 시점 도래 뒤 N영업일 안에 지급(기-28 [정책값 미정]). null 이면 미정 — FE 가 「[정책값 미정]」. */
    private Integer fixedFeePaymentBusinessDays;
}
