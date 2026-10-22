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
    INSPECTION_REJECTED("검수 반려"),
    /** 검수 사유 번복 — 착불(브랜드 귀책) 신청이 소비자 귀책으로(1021 기-17). */
    REASON_OVERTURNED("검수 사유 번복"),
    /** 검수 통과 때 브랜드 귀책으로 인정 — 선불 운임 · 최초 배송비 환급(1021 기-93). */
    FAULT_ACCEPTED_ON_PASS("브랜드 귀책 인정"),
    /** 택배 도착 후 N영업일 자동 입고(1021 기-17). */
    AUTO_RECEIVED("자동 입고"),
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
    DISPOSED("보관 기간 만료 · 폐기"),
    /** 반려에 대한 소비자 이의를 운영자가 인용 — 운영자 사유 환불로 종결(어드민 06b B2 · 1009 기획 수정본 8-3). */
    DISPUTE_ACCEPTED("반려 이의 인용 · 운영자 사유 환불"),
    /** 검수 지연 자동 알림 N회 무응답 — 운영자가 운영자 사유 환불로 종결(41 보고 4번 · 어드민 06b). */
    INSPECTION_UNANSWERED("검수 무응답 · 운영자 사유 환불");

    private final String label;
}
