package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.creator.type.CreatorBusinessType;

/**
 * 원천징수 표기 — 상대 계정 정보 기반 자동 전환(§25-5-6).
 * 실지급액은 계산하지 않는다(세무 확정 전).
 */
@Getter
@RequiredArgsConstructor
public enum WithholdingType {
    WITHHOLDING_3_3("원천징수 3.3%"),
    TAX_INVOICE("세금계산서 발행");

    private final String label;

    public static WithholdingType from(CreatorBusinessType businessType) {
        if (businessType == null) {
            return null;
        }
        return businessType == CreatorBusinessType.BUSINESS ? TAX_INVOICE : WITHHOLDING_3_3;
    }
}
