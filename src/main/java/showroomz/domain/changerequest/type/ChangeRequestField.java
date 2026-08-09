package showroomz.domain.changerequest.type;

import java.util.Arrays;
import java.util.List;

/**
 * 변경 요청 가능 항목만 정의한다(§0 ③) — 사업자등록번호처럼 변경 불가한 항목은
 * 이 enum에 존재하지 않으므로 어떤 경로로도 요청 레코드에 실릴 수 없다.
 */
public enum ChangeRequestField {
    MARKET_NAME("브랜드명", ChangeRequestType.BUSINESS_INFO),
    REPRESENTATIVE_NAME("대표자명", ChangeRequestType.BUSINESS_INFO),
    COMPANY_NAME("사업자등록증 상호", ChangeRequestType.BUSINESS_INFO),
    BUSINESS_CONDITION("업태", ChangeRequestType.BUSINESS_INFO),
    BUSINESS_ADDRESS("사업장 주소", ChangeRequestType.BUSINESS_INFO),
    MAIL_ORDER_REG_NUMBER("통신판매업 신고번호", ChangeRequestType.BUSINESS_INFO),
    BANK_CODE("은행", ChangeRequestType.SETTLEMENT_ACCOUNT),
    ACCOUNT_NUMBER("계좌번호", ChangeRequestType.SETTLEMENT_ACCOUNT),
    ACCOUNT_HOLDER("예금주", ChangeRequestType.SETTLEMENT_ACCOUNT);

    private final String label;
    private final ChangeRequestType type;

    ChangeRequestField(String label, ChangeRequestType type) {
        this.label = label;
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public ChangeRequestType getType() {
        return type;
    }

    /** 유형별 요청 가능 항목 — M1 체크박스 6종 / M2 3종 고정(§15-6)의 SoT. */
    public static List<ChangeRequestField> of(ChangeRequestType type) {
        return Arrays.stream(values()).filter(f -> f.type == type).toList();
    }
}
