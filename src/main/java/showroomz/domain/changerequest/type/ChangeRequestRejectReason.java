package showroomz.domain.changerequest.type;

import java.util.EnumSet;
import java.util.Set;

/**
 * §15-8·§16-5 정형 반려 사유. 입점 심사용 {@code RejectionReasonType}과 별도로 둔다 —
 * 목록이 다르고 공개 정책이 정반대(입점 인플루언서는 비공개, 변경 요청은 공개)다.
 * description은 가공 없이 브랜드 배너·통지 메일에 그대로 실리므로 문장형을 유지한다.
 */
public enum ChangeRequestRejectReason {
    EVIDENCE_MISSING("증빙 서류 미첨부", EnumSet.of(ChangeRequestType.BUSINESS_INFO)),
    EVIDENCE_VALUE_MISMATCH("증빙 서류와 요청 값이 일치하지 않음", EnumSet.of(ChangeRequestType.BUSINESS_INFO)),
    EVIDENCE_UNREADABLE("서류 판독 불가(흐림·잘림)", EnumSet.of(ChangeRequestType.BUSINESS_INFO)),
    EVIDENCE_EXPIRED("서류 유효기간 경과", EnumSet.of(ChangeRequestType.BUSINESS_INFO)),
    REASON_INSUFFICIENT("변경 사유 불충분", EnumSet.of(ChangeRequestType.BUSINESS_INFO)),
    BANKBOOK_MISSING("통장 사본 미첨부", EnumSet.of(ChangeRequestType.SETTLEMENT_ACCOUNT)),
    HOLDER_NAME_MISMATCH("예금주와 사업자 명의 불일치", EnumSet.of(ChangeRequestType.SETTLEMENT_ACCOUNT)),
    ACCOUNT_NUMBER_INVALID("계좌번호 오류·미개설 계좌", EnumSet.of(ChangeRequestType.SETTLEMENT_ACCOUNT)),
    BANKBOOK_UNREADABLE("통장 사본 판독 불가", EnumSet.of(ChangeRequestType.SETTLEMENT_ACCOUNT)),
    OTHER("기타", EnumSet.of(ChangeRequestType.BUSINESS_INFO, ChangeRequestType.SETTLEMENT_ACCOUNT));

    private final String description;
    private final Set<ChangeRequestType> supports;

    ChangeRequestRejectReason(String description, Set<ChangeRequestType> supports) {
        this.description = description;
        this.supports = supports;
    }

    public String getDescription() {
        return description;
    }

    public boolean supports(ChangeRequestType type) {
        return supports.contains(type);
    }

    /** OTHER를 선택했을 때만 상세 사유가 필수다(§16-5). */
    public boolean isDetailRequired() {
        return this == OTHER;
    }
}
