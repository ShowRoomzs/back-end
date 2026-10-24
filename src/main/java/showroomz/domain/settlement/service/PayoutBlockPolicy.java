package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.port.PayoutHoldReader;
import showroomz.domain.settlement.port.PendingAccountChangeReader;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;

import java.util.ArrayList;
import java.util.List;

/**
 * 수취자 몫 지급 보류 판정(44 어드민 설계서 5-4 · 정합 계획서 R1-5) — 확정(3-2 #2) · 재판정(지급 배치 앞단) · 지급 지시 앞단이 같은 식을 쓴다.
 * 인플루언서 몫은 세금계산서 · 주민등록번호 · 계좌 확인 · 계정 정지 사유로, 브랜드 몫은 <b>계정 정지(지급 정지 사유)</b>로만 막힌다 — 플랫폼
 * 몫은 예정일에 나간다(§41-5 「인플루언서 몫만 늦어진다」 · §51-3).
 *
 * <p>사업자는 인플루언서 세금계산서 문서 행이 {@code VERIFIED}일 때만 풀린다(운영자 M4 확인).
 *
 * <p>인플루언서 계정 정지(정산 정보 불일치 · 53 설계서 4-3)가 맨 앞 사유다 — 정지 중에는 다른 사유가 풀려도 지급하지 않는다. 해제가 이 판정을
 * 다시 불러 남은 사유를 본다({@code PayoutHoldTransitions}). 계좌 확인 보류도 같은 틀이다.
 */
@Component
@RequiredArgsConstructor
public class PayoutBlockPolicy {

    private final SettlementTaxDocumentRepository documentRepository;
    private final PayoutHoldReader payoutHoldReader;
    private final PendingAccountChangeReader pendingAccountChangeReader;

    public List<PayoutBlockReason> blockReasons(Settlement settlement) {
        List<PayoutBlockReason> reasons = new ArrayList<>();
        Long creatorId = settlement.getCreatorId();
        if (creatorId != null && payoutHoldReader.isHeld(SettlementPayee.CREATOR, creatorId)) {
            reasons.add(PayoutBlockReason.CREATOR_SUSPENDED);
        }
        Creator creator = settlement.getCreator();
        // 스튜디오 계좌 변경 뒤 실명 확인 전 · 실패 · 변경 필요(50 설계서 1-3) — 사업자 · 비사업자 모두. 확인 이력 없는 기존 회원은 막지 않는다.
        if (creator != null && creator.getAccountVerificationStatus() != null
                && creator.getAccountVerificationStatus().blocksPayout()) {
            reasons.add(PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED);
        } else if (pendingAccountChangeReader.hasPending(creatorId)) {
            // 계좌 변경 확인 중(1021 기-22 · 8-0 ②) — 확인 상태는 기존 값 그대로라 요청을 직접 본다. 확인이 끝날 때까지 기존 계좌로 내보내지 않는다.
            reasons.add(PayoutBlockReason.CREATOR_ACCOUNT_UNVERIFIED);
        }
        if (settlement.isBusinessCreator() && !isCreatorTaxInvoiceVerified(settlement)) {
            reasons.add(PayoutBlockReason.TAX_INVOICE_UNVERIFIED);
        }
        // 비사업자 주민등록번호 미등록은 지급을 막지 않는다(1021 결정 노트 기-12) — 원천징수영수증 생성만 등록 뒤로 미뤄진다.
        return reasons;
    }

    public boolean isBlocked(Settlement settlement) {
        return !blockReasons(settlement).isEmpty();
    }

    /**
     * 브랜드 몫 보류 사유 — 브랜드 계정 정지 · 지급 정지 사유(정산 정보 불일치 · 사업자 정보 허위 기재 · 52 설계서 6-3)뿐이다. 확정 · 지급 지시
     * 앞단이 이 판정으로 브랜드 행을 막는다.
     */
    public List<PayoutBlockReason> brandBlockReasons(Settlement settlement) {
        Long marketId = settlement.getMarketId();
        return marketId != null && payoutHoldReader.isHeld(SettlementPayee.BRAND, marketId)
                ? List.of(PayoutBlockReason.BRAND_SUSPENDED) : List.of();
    }

    public boolean isBrandBlocked(Settlement settlement) {
        return !brandBlockReasons(settlement).isEmpty();
    }

    /** 인플루언서 계정 정지로 리워드가 멈췄는가 — 다른 보류 사유와 무관하게 정지만 본다(세금계산서 확인 · 지시 앞단). */
    public boolean isCreatorSuspended(Long creatorId) {
        return creatorId != null && payoutHoldReader.isHeld(SettlementPayee.CREATOR, creatorId);
    }

    private boolean isCreatorTaxInvoiceVerified(Settlement settlement) {
        return documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(settlement.getId(),
                        TaxDocumentType.CREATOR_TAX_INVOICE)
                .map(d -> d.getStatus() == TaxDocumentStatus.VERIFIED)
                .orElse(false);
    }
}
