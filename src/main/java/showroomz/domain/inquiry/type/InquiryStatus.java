package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 문의 상태 (§17-1) — 접수 → 답변완료 단방향 1회. 처리중·종료는 두지 않는다.
 */
@Getter
@AllArgsConstructor
public enum InquiryStatus {

    WAITING("접수"),
    ANSWERED("답변완료");

    private final String description;
}
