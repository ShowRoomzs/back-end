package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 레일 「진행 중」 칩 8종(§52-3) — 「이 브랜드 밀린 거 있어?」. 숫자는 전부 다른 화면이 이미 세는 식을 쓴다(52 설계서 2-5).
 * 「열린 1:1 문의」는 파트너센터 1:1 문의가 정의되지 않아(§55 A-5) 운영팀 스레드 미확인 수로 센다(0-9).
 */
@Getter
@RequiredArgsConstructor
public enum BrandPendingChip {
    SHIP_OVERDUE("발송 기한 경과", "WARNING", "ORDERS"),
    CANCEL_RESPONSE_PENDING("취소 요청 응답 대기", "NEUTRAL", "ORDERS"),
    INSPECTION_PENDING("검수 대기", "NEUTRAL", "CLAIMS"),
    RESHIP_PENDING("재발송 대기", "NEUTRAL", "CLAIMS"),
    PRODUCT_INQUIRY_UNANSWERED("상품 문의 미답변", "NEUTRAL", "PRODUCT_INQUIRIES"),
    OPERATOR_THREAD_UNREAD("운영팀 스레드 미확인", "WARNING", "THREAD"),
    SETTLEMENT_ADJUSTING("조정 협의", "NEUTRAL", "SETTLEMENTS"),
    BRAND_TAX_INVOICE_PENDING("세금계산서 발행 대기", "NEUTRAL", "SETTLEMENTS");

    private final String label;
    private final String tone;
    /** 이동할 어드민 화면 — FE 가 marketId(THREAD 는 threadId)를 붙여 라우팅한다. */
    private final String target;
}
