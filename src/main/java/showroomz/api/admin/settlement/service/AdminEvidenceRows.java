package showroomz.api.admin.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.settlement.dto.AdminSettlementDto;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 07a 증빙 탭(44 어드민 설계서 7-2) — 행 = 문서 1건 · 처리 끝(확인 · 발행 · 생성 완료)은 뺀다. 비사업자 주민번호 미등록은 문서가 아니라
 * 보류 사유지만 「인플루언서 대기」 가상 행으로 함께 내린다.
 *
 * <p>열린 증빙은 정산 수에 비해 적다(확정 직후 · 대조 대기 · 발행 대기만) — 목록 전체를 만들고 메모리에서 기한 이른순 정렬 · 페이징한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEvidenceRows {

    private static final int BLOCKED_SCAN_LIMIT = 1000;

    private final SettlementTaxDocumentRepository documentRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementRepository settlementRepository;

    public record Rows(List<AdminSettlementDto.EvidenceItem> items, long operatorActionCount, long payoutBlockedCount) {
    }

    public Rows build() {
        List<SettlementTaxDocument> documents = documentRepository.findOpen(TaxDocumentStatus.DONE);
        List<SettlementPayout> blocked = payoutRepository.findBlockedCreatorPayouts(PageRequest.of(0, BLOCKED_SCAN_LIMIT));
        List<Long> settlementIds = new ArrayList<>(documents.stream().map(SettlementTaxDocument::getSettlementId).toList());
        blocked.forEach(p -> settlementIds.add(p.getSettlementId()));
        Map<Long, Settlement> settlements = settlementRepository.findAllById(settlementIds.stream().distinct().toList())
                .stream().collect(Collectors.toMap(Settlement::getId, Function.identity()));

        List<AdminSettlementDto.EvidenceItem> items = new ArrayList<>();
        long operatorAction = 0;
        long payoutBlocked = 0;
        for (SettlementTaxDocument d : documents) {
            Settlement s = settlements.get(d.getSettlementId());
            if (s == null) {
                continue;
            }
            boolean blocking = d.getType() == TaxDocumentType.CREATOR_TAX_INVOICE;
            if ((blocking && d.getStatus() == TaxDocumentStatus.SUBMITTED)
                    || (d.getType().isBrandInvoice() && d.getStatus() == TaxDocumentStatus.PENDING_ISSUE)) {
                operatorAction++;
            }
            if (blocking) {
                payoutBlocked++;
            }
            items.add(new AdminSettlementDto.EvidenceItem(d.getId(), s.getId(), s.getSettlementNumber(),
                    s.getContract().getTitle(), d.getType().isBrandInvoice() ? s.getMarket().getMarketName()
                    : s.getCreator().getShowroomName(), s.getCreatorBusinessType(), d.getType().name(),
                    d.getType().getLabel(), d.getType().getDirection(), d.getSubmittedAt(), d.getDueDate(),
                    d.getStatus().name(), d.getStatus().getLabel(), blocking ? "BLOCKING" : "NONE"));
        }
        for (SettlementPayout payout : blocked) {
            Settlement s = settlements.get(payout.getSettlementId());
            if (s == null || s.isBusinessCreator() || s.getCreator().getResidentRegistrationNumberEnc() != null) {
                continue;
            }
            payoutBlocked++;
            items.add(new AdminSettlementDto.EvidenceItem(null, s.getId(), s.getSettlementNumber(),
                    s.getContract().getTitle(), s.getCreator().getShowroomName(), s.getCreatorBusinessType(),
                    PayoutBlockReason.RESIDENT_NUMBER_MISSING.name(), PayoutBlockReason.RESIDENT_NUMBER_MISSING.getLabel(),
                    "인플루언서 → 플랫폼", null, null, "WAITING_CREATOR", "인플루언서 등록 대기", "BLOCKING"));
        }
        items.sort(Comparator.comparing(AdminSettlementDto.EvidenceItem::dueAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AdminSettlementDto.EvidenceItem::settlementId)
                .thenComparing(item -> item.documentId() == null ? Long.MAX_VALUE : item.documentId()));
        return new Rows(items, operatorAction, payoutBlocked);
    }
}
