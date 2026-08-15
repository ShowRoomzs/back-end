package showroomz.api.seller.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;

/**
 * 목록 상태 탭 (§23-2) — 배타적 단일선택. 두 축을 한 탭 줄에 늘어놓은 것이라
 * 앞의 둘은 답변 축을, 뒤의 둘은 노출 축을 건다. 기본 진입 탭은 전체다.
 */
@Getter
@AllArgsConstructor
public enum SellerInquiryStatusFilter {

    ALL("전체", null, null),
    WAITING("답변대기", InquiryStatus.WAITING, InquiryExposureStatus.NORMAL),
    ANSWERED("답변완료", InquiryStatus.ANSWERED, InquiryExposureStatus.NORMAL),
    DELETE_REQUESTED("삭제 요청", null, InquiryExposureStatus.DELETE_REQUESTED),
    DELETED("삭제", null, InquiryExposureStatus.DELETED);

    private final String description;
    private final InquiryStatus status;
    private final InquiryExposureStatus exposureStatus;
}
