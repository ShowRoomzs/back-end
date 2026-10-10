package showroomz.api.seller.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.api.common.settlement.service.SettlementClawbackViews;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.api.seller.settlement.dto.SellerSettlementDto;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.TaxDocumentStatus;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 파트너 상세의 연계 블록 — 조정 내역(이슈 스레드 모듈) · SHOWROOMZ 발행 세금계산서(증빙) · 차감 반영(클로백). 블록마다 원천 모듈이
 * 달라 조회 서비스에서 떼어 둔다. 해당 행이 없으면 null · [] 이다 — 서버가 안 내리는 것이지 FE 가 숨기는 것이 아니다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerSettlementBlocks {

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("MM.dd");

    private final SettlementPartyViews views;
    private final SettlementTaxDocumentRepository documentRepository;
    private final SettlementClawbackViews clawbackViews;

    /** 조정 내역(D4) — 협의가 없으면 null. */
    public SettlementPartyDto.AdjustmentBlock adjustmentOf(Settlement settlement) {
        return views.adjustmentOf(settlement.getId(), SettlementParty.SELLER);
    }

    /** 조정 협의 차례 — MY_TURN · OPEN_FLOOR. */
    public boolean canRespond(SettlementPartyDto.AdjustmentBlock adjustment) {
        return adjustment != null && ("MY_TURN".equals(adjustment.turn()) || "OPEN_FLOOR".equals(adjustment.turn()));
    }

    /**
     * SHOWROOMZ 발행 세금계산서 — 확정 뒤에만 부른다. 브랜드 세금계산서 1행 + 차감이 있으면 수정세금계산서 N행. 행은 확정 시점에 생기므로
     * 합의로 리워드가 바뀌어도 수정세금계산서가 생기지 않는다(확인 기간 중 발행하지 않는 이유 · §41-6).
     */
    public List<SellerSettlementDto.TaxDocument> taxDocumentsOf(Settlement settlement) {
        return documentRepository.findBySettlementIdOrderByIdAsc(settlement.getId()).stream()
                .filter(d -> d.getType().isBrandInvoice())
                .map(d -> new SellerSettlementDto.TaxDocument(d.getId(), d.getType().name(), d.getType().getLabel(),
                        d.getSupplyAmount(), d.getVatAmount(), d.getTotalAmount(), d.getStatus().name(),
                        d.getStatus() == TaxDocumentStatus.PENDING_ISSUE && d.getDueDate() != null
                                ? "발행 대기 · 운영팀이 %s까지 발행합니다".formatted(d.getDueDate().format(DUE))
                                : d.getStatus().getLabel(),
                        d.getDueDate(), d.getIssuedDate(), d.getApprovalNumber(),
                        d.getStatus() == TaxDocumentStatus.ISSUED && d.hasFile()))
                .toList();
    }

    /** 차감 반영(D3) — 이 정산에 반영된 브랜드 측 차감. */
    public List<SellerSettlementDto.Clawback> clawbacksOf(Settlement settlement) {
        return clawbackViews.appliedTo(settlement.getId(), ClawbackSide.BRAND).stream()
                .map(row -> new SellerSettlementDto.Clawback(row.clawback().getClawbackNumber(),
                        row.origin() == null ? null : row.origin().getSettlementNumber(),
                        row.origin() == null ? null : row.origin().getContract().getTitle(), row.orderNumber(),
                        row.reasonLabel(), row.clawback().getAmount(),
                        row.creditInvoice() == null ? null : row.creditInvoice().getStatus().name()))
                .toList();
    }
}
