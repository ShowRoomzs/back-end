package showroomz.api.common.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.service.SettlementClawbackService;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.TaxDocumentType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 차감 행 표시 조각(44 어드민 설계서 6절 · 파트너 · 스튜디오 D3) — 차감 행에 원 정산 · 주문 항목 · 수정세금계산서를 붙인다. 세 서피스가
 * 같은 행을 같은 규칙으로 읽게 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementClawbackViews {

    private final SettlementClawbackRepository clawbackRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementTaxDocumentRepository documentRepository;

    /** 차감 1행 + 표시용 연결 — 원 정산 · 주문 항목(없을 수 있다) · 수정세금계산서(브랜드 측 반영 행만). */
    public record Row(SettlementClawback clawback, Settlement origin, SettlementItem item,
                      SettlementTaxDocument creditInvoice) {

        public String reasonLabel() {
            return SettlementClawbackService.reasonLabel(clawback.getReason());
        }

        public String orderNumber() {
            return item == null ? null : item.getOrderNumber();
        }

        public String productName() {
            return item == null ? null : item.getProductName();
        }
    }

    /** 이 정산에 반영된 한 측의 차감 — 파트너(브랜드) · 스튜디오(인플루언서) D3. */
    public List<Row> appliedTo(Long settlementId, ClawbackSide side) {
        return rows(clawbackRepository.findByAppliedSettlementIdOrderByIdAsc(settlementId).stream()
                .filter(c -> c.getSide() == side).toList());
    }

    /** 이 정산에 반영된 차감 전부 — 어드민 07b D3. */
    public List<Row> appliedTo(Long settlementId) {
        return rows(clawbackRepository.findByAppliedSettlementIdOrderByIdAsc(settlementId));
    }

    public List<Row> rows(Collection<SettlementClawback> clawbacks) {
        if (clawbacks.isEmpty()) {
            return List.of();
        }
        Map<Long, Settlement> origins = settlementRepository.findAllById(clawbacks.stream()
                        .map(SettlementClawback::getOriginSettlementId).distinct().toList()).stream()
                .collect(Collectors.toMap(Settlement::getId, Function.identity()));
        Map<Long, SettlementItem> items = itemRepository.findByOrderProductIdIn(clawbacks.stream()
                        .map(SettlementClawback::getOrderProductId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(SettlementItem::getOrderProductId, Function.identity(), (a, b) -> a));
        Map<Long, SettlementTaxDocument> credits = new LinkedHashMap<>();
        List<Long> appliedSettlementIds = clawbacks.stream().map(SettlementClawback::getAppliedSettlementId)
                .filter(Objects::nonNull).distinct().toList();
        if (!appliedSettlementIds.isEmpty()) {
            documentRepository.findBySettlementIdIn(appliedSettlementIds).stream()
                    .filter(d -> d.getType() == TaxDocumentType.BRAND_TAX_INVOICE_CREDIT && d.getClawbackId() != null)
                    .forEach(d -> credits.put(d.getClawbackId(), d));
        }
        return clawbacks.stream().map(c -> new Row(c, origins.get(c.getOriginSettlementId()),
                c.getOrderProductId() == null ? null : items.get(c.getOrderProductId()), credits.get(c.getId())))
                .toList();
    }

    /** 원 정산에서 내 몫이 지급된 시각 — 스튜디오 「원 정산 지급일」. */
    public LocalDateTime creatorPaidAt(Long originSettlementId) {
        return payoutRepository.findBySettlementIdAndPayee(originSettlementId, SettlementPayee.CREATOR)
                .map(SettlementPayout::getPaidAt).orElse(null);
    }
}
