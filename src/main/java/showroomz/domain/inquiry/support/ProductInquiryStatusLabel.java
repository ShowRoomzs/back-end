package showroomz.domain.inquiry.support;

import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;

/**
 * 답변 축과 노출 축을 한 열에 합쳐 보여줄 때 쓰는 상태 라벨 (§23-1).
 * 노출 축에 값이 있으면 그쪽이 앞선다 — 두 값 자체는 내부적으로 그대로 보존된다.
 */
public final class ProductInquiryStatusLabel {

    private ProductInquiryStatusLabel() {
    }

    public static String of(InquiryStatus status, InquiryExposureStatus exposureStatus) {
        if (exposureStatus != null && exposureStatus != InquiryExposureStatus.NORMAL) {
            return exposureStatus.getDescription();
        }
        return status == InquiryStatus.ANSWERED ? "답변완료" : "답변대기";
    }
}
