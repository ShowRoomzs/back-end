package showroomz.api.admin.market.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RejectionReasonType {
    INSUFFICIENT_DOCUMENTS("서류 미비"),
    BUSINESS_REG_NUMBER_MISMATCH("사업자등록번호 불일치"),
    MAIL_ORDER_REPORT_INCOMPLETE("통신판매업신고 미완료"),
    BANK_ACCOUNT_ERROR("계좌 정보 오류"),
    DUPLICATE_APPLICATION("중복 신청"),
    OTHER("기타");

    private final String description;
}
