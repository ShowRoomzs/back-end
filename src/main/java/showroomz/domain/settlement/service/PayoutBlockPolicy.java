package showroomz.domain.settlement.service;

import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.type.PayoutBlockReason;

import java.util.List;

/**
 * 인플루언서 몫 지급 보류 판정(44 어드민 설계서 5-4) — 확정(3-2 #2) · 재판정(지급 배치 앞단)이 같은 식을 쓴다. 보류는 인플루언서
 * 몫에만 걸린다 — 브랜드 · 플랫폼 몫은 예정일에 나간다(§41-5 「인플루언서 몫만 늦어진다」).
 *
 * <p>사업자의 세금계산서 확인 여부는 증빙 모듈(단계 7)이 생기기 전까지 「사업자면 항상 보류」다.
 */
@Component
public class PayoutBlockPolicy {

    public List<PayoutBlockReason> blockReasons(Settlement settlement) {
        if (settlement.isBusinessCreator()) {
            return isCreatorTaxInvoiceVerified(settlement) ? List.of() : List.of(PayoutBlockReason.TAX_INVOICE_UNVERIFIED);
        }
        Creator creator = settlement.getCreator();
        return creator == null || creator.getResidentRegistrationNumberEnc() == null
                ? List.of(PayoutBlockReason.RESIDENT_NUMBER_MISSING) : List.of();
    }

    public boolean isBlocked(Settlement settlement) {
        return !blockReasons(settlement).isEmpty();
    }

    /** 증빙 모듈 전 — 확인된 세금계산서가 있을 수 없다. */
    protected boolean isCreatorTaxInvoiceVerified(Settlement settlement) {
        return false;
    }
}
