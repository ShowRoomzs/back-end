package showroomz.api.creator.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.api.creator.settlement.dto.CreatorSettlementDto;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.entity.Settlement;

import java.util.List;

/**
 * 스튜디오 상세의 연계 블록 — 조정 내역(이슈 스레드 모듈) · 세금계산서 카드 · 원천징수영수증(증빙) · 차감 반영(클로백). 블록마다 원천
 * 모듈이 달라 조회 서비스에서 떼어 둔다. 원천 모듈이 붙기 전에는 비어 있다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorSettlementBlocks {

    private final SettlementPartyViews views;

    /** 조정 내역(D2) — 협의가 없으면 null. */
    public SettlementPartyDto.AdjustmentBlock adjustmentOf(Settlement settlement) {
        return views.adjustmentOf(settlement.getId(), SettlementParty.CREATOR);
    }

    /** 세금계산서 카드(D5 ~ D5c) — 사업자 ∧ 확정 후에만 부른다. */
    public CreatorSettlementDto.TaxInvoice taxInvoiceOf(Settlement settlement) {
        return null;
    }

    /** 원천징수영수증을 내려받을 수 있는가 — 지급 완료 ∧ 생성 완료. */
    public boolean receiptAvailable(Settlement settlement) {
        return false;
    }

    /** 사업자 보류 행의 상태 문구 — 세금계산서 카드 상태를 따른다(발행 필요 · 확인 중 · 반려 · 재제출 필요). */
    public String businessBlockedLabel(Settlement settlement) {
        return "발행 필요";
    }

    /** 사업자 보류 행의 지급 예정 문장 — 「발행 확인 후 + 3영업일」 · 「확인 후 + 3영업일」 · 「재제출 확인 후 + 3영업일」. */
    public String businessBlockedNote(Settlement settlement, int payoutBusinessDays) {
        return "발행 확인 후 + %d영업일".formatted(payoutBusinessDays);
    }

    /** GNB 배지 가산 — 세금계산서 입력 대기 · 반려 건수. */
    public long taxInvoiceAttentionCount(Long creatorId) {
        return 0;
    }

    /** 차감 반영(D3) — 이 정산에 반영된 인플루언서 측 차감. */
    public List<CreatorSettlementDto.Clawback> clawbacksOf(Settlement settlement) {
        return List.of();
    }
}
