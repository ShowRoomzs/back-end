package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.port.CreatorPayoutHoldReader;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;

import java.util.ArrayList;
import java.util.List;

/**
 * 인플루언서 몫 지급 보류 판정(44 어드민 설계서 5-4) — 확정(3-2 #2) · 재판정(지급 배치 앞단)이 같은 식을 쓴다. 보류는 인플루언서
 * 몫에만 걸린다 — 브랜드 · 플랫폼 몫은 예정일에 나간다(§41-5 「인플루언서 몫만 늦어진다」).
 *
 * <p>사업자는 인플루언서 세금계산서 문서 행이 {@code VERIFIED}일 때만 풀린다(운영자 M4 확인).
 *
 * <p>인플루언서 계정 정지(정산 정보 불일치 · 53 설계서 4-3)가 맨 앞 사유다 — 정지 중에는 다른 사유가 풀려도 지급하지 않는다. 해제가 이 판정을
 * 다시 불러 남은 사유를 본다({@code CreatorSettlementSanctionHook}).
 */
@Component
@RequiredArgsConstructor
public class PayoutBlockPolicy {

    private final SettlementTaxDocumentRepository documentRepository;
    private final CreatorPayoutHoldReader creatorPayoutHoldReader;

    public List<PayoutBlockReason> blockReasons(Settlement settlement) {
        List<PayoutBlockReason> reasons = new ArrayList<>();
        Long creatorId = settlement.getCreatorId();
        if (creatorId != null && creatorPayoutHoldReader.isHeld(creatorId)) {
            reasons.add(PayoutBlockReason.CREATOR_SUSPENDED);
        }
        if (settlement.isBusinessCreator()) {
            if (!isCreatorTaxInvoiceVerified(settlement)) {
                reasons.add(PayoutBlockReason.TAX_INVOICE_UNVERIFIED);
            }
            return reasons;
        }
        Creator creator = settlement.getCreator();
        if (creator == null || creator.getResidentRegistrationNumberEnc() == null) {
            reasons.add(PayoutBlockReason.RESIDENT_NUMBER_MISSING);
        }
        return reasons;
    }

    public boolean isBlocked(Settlement settlement) {
        return !blockReasons(settlement).isEmpty();
    }

    private boolean isCreatorTaxInvoiceVerified(Settlement settlement) {
        return documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(settlement.getId(),
                        TaxDocumentType.CREATOR_TAX_INVOICE)
                .map(d -> d.getStatus() == TaxDocumentStatus.VERIFIED)
                .orElse(false);
    }
}
