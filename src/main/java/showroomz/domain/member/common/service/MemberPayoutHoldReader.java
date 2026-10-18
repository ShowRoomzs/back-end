package showroomz.domain.member.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.settlement.port.PayoutHoldReader;
import showroomz.domain.settlement.type.SettlementPayee;

/**
 * 정산 포트 {@link PayoutHoldReader}의 구현 — 열린 정지 ∧ 지급 정지 사유. 정지 행의 {@code payout_held} 스냅샷을 본다 — 사유별 규칙이 뒤에
 * 바뀌어도 이미 내린 정지의 효과는 그대로다(52 설계서 6-3 · 53 설계서 4-3).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberPayoutHoldReader implements PayoutHoldReader {

    private final BrandSanctionRepository brandSanctions;
    private final CreatorSanctionRepository creatorSanctions;

    @Override
    public boolean isHeld(SettlementPayee payee, Long refId) {
        if (payee == null || refId == null) {
            return false;
        }
        return switch (payee) {
            case BRAND -> brandSanctions.existsOpenPayoutHold(refId);
            case CREATOR -> creatorSanctions.existsOpenPayoutHold(refId);
            case PLATFORM -> false;
        };
    }
}
