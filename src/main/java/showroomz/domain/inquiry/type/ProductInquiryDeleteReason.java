package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 브랜드의 문의 삭제 요청 사유 (§23-5) — [근거 대기] 목록 잠정. */
@Getter
@AllArgsConstructor
public enum ProductInquiryDeleteReason {

    ABUSE("비방·욕설"),
    PRIVACY_EXPOSURE("개인정보 노출"),
    ADVERTISEMENT("광고·홍보"),
    BRAND_COMPARISON("타 브랜드 비교·비방"),
    ETC("기타(직접 입력)");

    private final String description;

    /** 기타는 상세 설명이 필수다 (§23-5) */
    public boolean requiresDetail() {
        return this == ETC;
    }
}
