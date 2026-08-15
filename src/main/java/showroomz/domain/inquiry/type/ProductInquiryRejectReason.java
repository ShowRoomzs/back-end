package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 운영자의 삭제 요청 반려 사유 (§18-6) — 요청 브랜드에게 전달된다.
 * 삭제 사유({@link ProductInquiryAdminDeleteReason})와 달리 상대(요청 브랜드)가 있는 판단이라 공개 범위가 다르다.
 */
@Getter
@AllArgsConstructor
public enum ProductInquiryRejectReason {

    NOT_QUALIFYING("삭제 기준에 해당하지 않음"),
    INSUFFICIENT_EVIDENCE("근거 부족 — 사실관계 확인 불가"),
    NORMAL_INQUIRY("정상적인 상품 문의"),
    ETC("기타(직접 입력)");

    private final String description;

    /** 기타는 상세 사유가 필수다 (§18-6) */
    public boolean requiresDetail() {
        return this == ETC;
    }
}
