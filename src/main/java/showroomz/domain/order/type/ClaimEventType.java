package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 클레임 처리 이력의 사건(35 설계서 1-9). */
@Getter
@RequiredArgsConstructor
public enum ClaimEventType {
    REQUESTED("신청 접수 · 자동 수락"),
    COLLECTION_INVOICE_REGISTERED("회수 송장 입력"),
    COLLECTION_INVOICE_UPDATED("회수 송장 정정"),
    ARRIVED("회수 완료 · 브랜드 도착"),
    RECEIVED("입고 확인"),
    INSPECTION_PASSED("검수 통과"),
    INSPECTION_REJECTED("검수 거절"),
    RESHIP_FEE_PAID("소비자 재배송비 결제"),
    RESHIP_FEE_SETTLED("재배송비 정산"),
    WITHDRAWN("요청 철회"),
    INVOICE_EXPIRED("회수 송장 미등록 · 자동 취소"),
    CLOSED_BY_ADMIN("운영자 직권 종결"),
    RESHIP_ADDRESS_CHANGED("교환받을 배송지 변경"),
    RESHIP_INVOICE_REGISTERED("재발송 송장 등록"),
    RESHIP_INVOICE_UPDATED("재발송 송장 수정"),
    RESHIP_DELIVERED("재발송 도착"),
    REFUND_EXECUTED("환불 집행"),
    STORAGE_NOTICE_SENT("미결제 고지"),
    DISPOSED("보관 기간 만료 · 폐기");

    private final String label;
}
