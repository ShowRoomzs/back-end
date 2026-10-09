package showroomz.api.seller.settlement.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.api.seller.settlement.dto.SellerSettlementDto;
import showroomz.domain.settlement.entity.Settlement;

import java.util.List;

/**
 * 파트너 상세의 연계 블록 — 조정 내역(이슈 스레드 모듈) · SHOWROOMZ 발행 세금계산서(증빙) · 차감 반영(클로백). 블록마다 원천 모듈이
 * 달라 조회 서비스에서 떼어 둔다. 원천 모듈이 붙기 전에는 비어 있다 — 서버가 안 내리는 것이지 FE 가 숨기는 것이 아니다.
 */
@Component
@Transactional(readOnly = true)
public class SellerSettlementBlocks {

    /** 조정 내역(D4) — 협의가 없으면 null. */
    public SettlementPartyDto.AdjustmentBlock adjustmentOf(Settlement settlement) {
        return null;
    }

    /** 조정 협의 차례 — MY_TURN · OPEN_FLOOR. */
    public boolean canRespond(SettlementPartyDto.AdjustmentBlock adjustment) {
        return adjustment != null && ("MY_TURN".equals(adjustment.turn()) || "OPEN_FLOOR".equals(adjustment.turn()));
    }

    /** SHOWROOMZ 발행 세금계산서 — 확정 뒤에만 부른다. */
    public List<SellerSettlementDto.TaxDocument> taxDocumentsOf(Settlement settlement) {
        return List.of();
    }

    /** 차감 반영(D3) — 이 정산에 반영된 브랜드 측 차감. */
    public List<SellerSettlementDto.Clawback> clawbacksOf(Settlement settlement) {
        return List.of();
    }
}
