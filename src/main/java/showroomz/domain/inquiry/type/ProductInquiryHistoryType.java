package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 상품 문의 처리 이력 이벤트 (§23-3 우측 · 처리 이력) — 최신순으로 보여준다. */
@Getter
@AllArgsConstructor
public enum ProductInquiryHistoryType {

    REGISTERED("문의 등록", InquiryActorType.CONSUMER),
    ANSWERED("답변 등록 · 공개 노출 시작", InquiryActorType.BRAND),
    ANSWER_MODIFIED("답변 수정", InquiryActorType.BRAND),
    DELETE_REQUESTED("문의 삭제 요청", InquiryActorType.BRAND),
    DELETE_REJECTED("삭제 요청 반려 · 결과 알림 수신", InquiryActorType.OPERATOR),
    DELETE_EXECUTED("문의 삭제 집행", InquiryActorType.OPERATOR);

    private final String description;
    private final InquiryActorType actorType;
}
