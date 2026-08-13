package showroomz.api.admin.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.inquiry.type.InquiryStatus;

/** 목록 상태 탭 (§17-2) — 기본 진입 탭은 전체다. */
@Getter
@AllArgsConstructor
public enum AdminInquiryStatusFilter {

    ALL("전체", null),
    WAITING("접수", InquiryStatus.WAITING),
    ANSWERED("답변완료", InquiryStatus.ANSWERED);

    private final String description;
    private final InquiryStatus status;
}
