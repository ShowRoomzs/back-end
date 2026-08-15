package showroomz.api.admin.productinquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;

/** 목록 상태 탭 (§18-2) — 배타적 단일선택. 기본 진입 탭은 전체다. */
@Getter
@AllArgsConstructor
public enum AdminProductInquiryStatusFilter {

    ALL("전체", null, null),
    WAITING("답변대기", InquiryStatus.WAITING, InquiryExposureStatus.NORMAL),
    ANSWERED("답변완료", InquiryStatus.ANSWERED, InquiryExposureStatus.NORMAL),
    DELETE_REQUESTED("삭제 요청", null, InquiryExposureStatus.DELETE_REQUESTED),
    DELETED("삭제", null, InquiryExposureStatus.DELETED);

    private final String description;
    private final InquiryStatus status;
    private final InquiryExposureStatus exposureStatus;
}
