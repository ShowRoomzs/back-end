package showroomz.api.creator.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.api.common.settlement.service.SettlementClawbackViews;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.api.creator.settlement.dto.CreatorSettlementDto;
import showroomz.domain.settlement.adjustment.type.SettlementParty;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.config.properties.SettlementProperties;

import java.util.List;
import java.util.Optional;

/**
 * 스튜디오 상세의 연계 블록 — 조정 내역(이슈 스레드 모듈) · 세금계산서 카드 · 원천징수영수증(증빙) · 차감 반영(클로백). 블록마다 원천
 * 모듈이 달라 조회 서비스에서 떼어 둔다. 해당 행이 없으면 null · [] 이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorSettlementBlocks {

    private final SettlementPartyViews views;
    private final SettlementTaxDocumentRepository documentRepository;
    private final SettlementProperties properties;
    private final SettlementClawbackViews clawbackViews;

    /** 조정 내역(D2) — 협의가 없으면 null. */
    public SettlementPartyDto.AdjustmentBlock adjustmentOf(Settlement settlement) {
        return views.adjustmentOf(settlement.getId(), SettlementParty.CREATOR);
    }

    /** 세금계산서 카드(D5 ~ D5c) — 사업자 ∧ 확정 후에만 부른다. 문서 행이 없으면(0원 리워드) null. */
    public CreatorSettlementDto.TaxInvoice taxInvoiceOf(Settlement settlement) {
        return creatorInvoice(settlement).map(this::taxInvoice).orElse(null);
    }

    public CreatorSettlementDto.TaxInvoice taxInvoice(SettlementTaxDocument d) {
        SettlementProperties.Platform platform = properties.getPlatform();
        return new CreatorSettlementDto.TaxInvoice(cardStatus(d.getStatus()),
                new CreatorSettlementDto.Supplier(platform.getBusinessName(), platform.getRepresentative(),
                        platform.getRegistrationNumber(), platform.getAddress(), platform.getTaxEmail()),
                d.getSupplyAmount(), d.getVatAmount(), d.getTotalAmount(), d.getApprovalNumber(), d.getSubmittedAt(),
                d.getFileName(), d.getRejectReason() == null ? null : d.getRejectReason().name(),
                d.getRejectReason() == null ? null : d.getRejectReason().getLabel(), d.getRejectedAt(),
                d.getVerifiedAt());
    }

    /** 원천징수영수증을 내려받을 수 있는가 — 생성 완료(지급 완료 뒤 비동기로 만들어진다). */
    public boolean receiptAvailable(Settlement settlement) {
        return documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(settlement.getId(),
                        TaxDocumentType.WITHHOLDING_RECEIPT)
                .map(d -> d.getStatus() == TaxDocumentStatus.GENERATED && d.hasFile())
                .orElse(false);
    }

    /** 사업자 보류 행의 상태 문구 — 세금계산서 카드 상태를 따른다(발행 필요 · 확인 중 · 반려 · 재제출 필요). */
    public String businessBlockedLabel(Settlement settlement) {
        TaxDocumentStatus status = creatorInvoice(settlement).map(SettlementTaxDocument::getStatus).orElse(null);
        if (status == TaxDocumentStatus.SUBMITTED) {
            return "확인 중";
        }
        if (status == TaxDocumentStatus.REJECTED) {
            return "반려 · 재제출 필요";
        }
        return "발행 필요";
    }

    /** 사업자 보류 행의 지급 예정 문장 — 「발행 확인 후 + 3영업일」 · 「확인 후 + 3영업일」 · 「재제출 확인 후 + 3영업일」. */
    public String businessBlockedNote(Settlement settlement, int payoutBusinessDays) {
        TaxDocumentStatus status = creatorInvoice(settlement).map(SettlementTaxDocument::getStatus).orElse(null);
        String prefix = status == TaxDocumentStatus.SUBMITTED ? "확인 후"
                : status == TaxDocumentStatus.REJECTED ? "재제출 확인 후" : "발행 확인 후";
        return "%s + %d영업일".formatted(prefix, payoutBusinessDays);
    }

    /** GNB 배지 가산 — 세금계산서 입력 대기 · 반려 건수. */
    public long taxInvoiceAttentionCount(Long creatorId) {
        return documentRepository.countCreatorInvoicesToInput(creatorId);
    }

    /** 차감 반영(D3) — 이 정산에 반영된 인플루언서 측 차감. */
    public List<CreatorSettlementDto.Clawback> clawbacksOf(Settlement settlement) {
        return clawbackViews.appliedTo(settlement.getId(), ClawbackSide.CREATOR).stream()
                .map(row -> new CreatorSettlementDto.Clawback(row.clawback().getClawbackNumber(), row.orderNumber(),
                        row.origin() == null ? null : row.origin().getContract().getTitle(),
                        clawbackViews.creatorPaidAt(row.clawback().getOriginSettlementId()), row.reasonLabel(),
                        row.clawback().getRefundAmount(), row.clawback().getAmount(),
                        row.item() == null ? null : row.item().getRewardRate(), row.clawback().getCreatedAt()))
                .toList();
    }

    public Optional<SettlementTaxDocument> creatorInvoice(Settlement settlement) {
        return documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(settlement.getId(),
                TaxDocumentType.CREATOR_TAX_INVOICE);
    }

    private static String cardStatus(TaxDocumentStatus status) {
        return switch (status) {
            case SUBMITTED, REJECTED, VERIFIED -> status.name();
            default -> TaxDocumentStatus.PENDING_INPUT.name();
        };
    }
}
