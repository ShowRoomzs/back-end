package showroomz.domain.changerequest.type;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 변경 요청 가능 항목만 정의한다(§0 ③) — 브랜드의 사업자등록번호처럼 변경 불가한 항목은 이 enum에 존재하지 않으므로 어떤 경로로도
 * 브랜드 요청 레코드에 실릴 수 없다. 인플루언서 사업자 구분 변경(§16-2a)의 항목은 별도 키다(16 설계서 2-2) — 한 항목이 두 방향에 속할
 * 수 있어 유형을 집합으로 든다. 주민등록번호 항목에는 <b>마스킹 값만</b> 싣는다(원문은 요청 행의 암호문 컬럼).
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
    ACCOUNT_HOLDER("예금주", ChangeRequestType.SETTLEMENT_ACCOUNT),
    CREATOR_BUSINESS_TYPE("사업자 구분", ChangeRequestType.TO_BUSINESS, ChangeRequestType.TO_INDIVIDUAL),
    CREATOR_BUSINESS_REG_NUMBER("사업자등록번호", ChangeRequestType.TO_BUSINESS),
    CREATOR_RESIDENT_NUMBER("주민등록번호", ChangeRequestType.TO_INDIVIDUAL),
    /** 인플루언서 계좌 변경(1021 기-22) — 계좌번호는 마스킹 값으로 남기고 원문은 요청 행 암호문에. */
    CREATOR_ACCOUNT_BANK("은행", ChangeRequestType.CREATOR_ACCOUNT),
    CREATOR_ACCOUNT_NUMBER("계좌번호", ChangeRequestType.CREATOR_ACCOUNT);

    private final String label;
    private final Set<ChangeRequestType> types;

    ChangeRequestField(String label, ChangeRequestType first, ChangeRequestType... rest) {
        this.label = label;
        this.types = EnumSet.of(first, rest);
    }

    public String getLabel() {
        return label;
    }

    public Set<ChangeRequestType> getTypes() {
        return types;
    }

    /** 유형별 요청 가능 항목 — M1 체크박스 6종 / M2 3종 고정(§15-6)의 SoT. */
    public static List<ChangeRequestField> of(ChangeRequestType type) {
        return Arrays.stream(values()).filter(f -> f.types.contains(type)).toList();
    }
}
