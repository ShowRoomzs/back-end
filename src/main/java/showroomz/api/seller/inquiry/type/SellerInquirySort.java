package showroomz.api.seller.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 목록 정렬 (§23-2) — 기본은 답변대기 우선이다. */
@Getter
@AllArgsConstructor
public enum SellerInquirySort {

    WAITING_FIRST("답변대기 우선"),
    CREATED_AT("등록일순");

    private final String description;
}
