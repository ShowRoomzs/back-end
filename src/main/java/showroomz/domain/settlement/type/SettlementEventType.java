package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 정산 이력(44 어드민 설계서 1-1) — 07b 처리 이력 문구와 1:1. 조정 계열은 이슈 스레드 모듈이 포트를 통해 쓴다. */
@Getter
@RequiredArgsConstructor
public enum SettlementEventType {
    CREATED("정산 생성 · 금액 공개"),
    ADJUSTMENT_REQUESTED("정산 조정 요청"),
    ADJUSTMENT_COUNTERED("다른 금액 제안"),
    ADJUSTMENT_REJECTED("조정 반대"),
    ADJUSTMENT_ACCEPTED("조정 동의"),
    ADJUSTMENT_DEADLINE_NOTICED("합의 기한 D-1 안내"),
    ADJUSTMENT_EXPIRED("합의 기한 경과"),
    AUTO_CONFIRMED("자동 확정"),
    CONFIRMED_BY_AGREEMENT("합의 확정"),
    CONFIRMED_BY_EXPIRY("기한 만료 확정"),
    TAX_INVOICE_REQUESTED("세금계산서 발행 요청"),
    TAX_INVOICE_SUBMITTED("세금계산서 승인번호 입력"),
    TAX_INVOICE_VERIFIED("세금계산서 확인"),
    TAX_INVOICE_REJECTED("세금계산서 반려"),
    BRAND_INVOICE_ISSUED("브랜드 세금계산서 발행본 등록"),
    PAYOUT_REQUESTED("지급 지시"),
    PAYOUT_PAID("지급 완료"),
    PAYOUT_FAILED("분배 실패"),
    PAYOUT_RETRIED("재분배"),
    WITHHOLDING_RECEIPT_GENERATED("원천징수영수증 생성"),
    CLAWBACK_REGISTERED("차감 발생"),
    CLAWBACK_APPLIED("차감 반영"),
    CLAWBACK_UNRECOVERABLE("차감 미회수");

    private final String label;
}
