package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 상품 문의 노출 축 (§23-1) — 답변 축({@link InquiryStatus})과 별개 값이다.
 * 목록에서는 한 열에 합쳐 보이지만, 삭제 요청 중에도 답변 축 값이 보존돼야
 * 반려 시 요청 직전 상태로 정확히 되돌아간다 (§23-5).
 */
@Getter
@AllArgsConstructor
public enum InquiryExposureStatus {

    NORMAL("정상"),
    DELETE_REQUESTED("삭제 요청"),
    DELETED("삭제");

    private final String description;

    /** 소비자 화면 노출 여부 — 삭제 집행 건은 질문·답변이 함께 내려간다 (§23-5) */
    public boolean isVisibleToConsumer() {
        return this != DELETED;
    }
}
