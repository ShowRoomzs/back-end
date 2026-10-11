package showroomz.api.seller.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.SettlementFile;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.api.seller.settlement.dto.SellerSettlementDto;
import showroomz.domain.contract.type.WithholdingType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.order.repository.OrderClaimCollectionRepository;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.service.SettlementStatementExcel;
import showroomz.domain.settlement.service.SettlementTaxDocumentService;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatementColumn;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.SettlementAccountMasker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 파트너센터 정산 관리 조회(44 파트너 설계서 2절) — 같은 정산 행을 브랜드 창으로 읽는다. 산식 · 상태 전이는 정산 도메인의 것이고
 * 여기는 「어느 열을 어떻게 내리는가」만 한다. 분배 실패는 지급 완료로 접는다(0-4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerSettlementQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_SIZE = 5;

    private final SellerSettlementAccessGuard accessGuard;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final OrderClaimCollectionRepository claimCollectionRepository;
    private final SettlementPartyViews views;
    private final SettlementStatementExcel statementExcel;
    private final SettlementProperties properties;
    private final SellerSettlementBlocks blocks;
    private final SettlementTaxDocumentRepository taxDocumentRepository;
    private final SettlementTaxDocumentService taxDocumentService;

    // ------------------------------------------------------------------ 목록(2-2)

    public PageResponse<SellerSettlementDto.ListItem> getSettlements(String sellerEmail, Set<SettlementStatus> statuses,
                                                                      String keyword, SettlementPartySort sort,
                                                                      PagingRequest paging) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        if (statuses != null && statuses.contains(SettlementStatus.PAYOUT_FAILED)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PAYOUT_FAILED 는 필터로 받지 않습니다 — PAID 에 포함됩니다.");
        }
        Page<Settlement> page = settlementRepository.searchForMarket(market.getId(),
                SettlementStatus.expandPartyFilter(statuses), keyword,
                sort == null ? SettlementPartySort.CREATED_DESC : sort, pageable(paging));
        List<Long> ids = page.getContent().stream().map(Settlement::getId).toList();
        Map<Long, Map<SettlementPayee, SettlementPayout>> payouts = views.payoutsOf(ids);
        return new PageResponse<>(page.getContent().stream()
                .map(s -> listItem(s, payouts.getOrDefault(s.getId(), Map.of()).get(SettlementPayee.BRAND))).toList(),
                page);
    }

    private SellerSettlementDto.ListItem listItem(Settlement s, SettlementPayout brand) {
        SettlementStatus status = s.getStatus().toPartyStatus();
        return new SellerSettlementDto.ListItem(s.getId(), s.getSettlementNumber(), s.getGroupBuyId(),
                s.getContract().getTitle(), influencer(s), s.getPeriodStartAt(), s.getPeriodEndAt(),
                s.getConfirmedSalesAmount(), s.getPgFeeAmount() + s.getPlatformFeeAmount(), s.getRewardAmount(),
                s.getRewardVatAmount(), s.getBrandPayoutAmount(), status, status.getLabel(), status.getTone(),
                schedule(s, brand));
    }

    /** 「지급(예정)일」 — 브랜드 행 기준. 사업자 인플루언서 건은 인플루언서 행과 날짜가 다르지만 브랜드는 자기 행만 본다. */
    private static SellerSettlementDto.Schedule schedule(Settlement s, SettlementPayout brand) {
        if (s.getStatus() == SettlementStatus.REVIEWING) {
            return new SellerSettlementDto.Schedule("REVIEW_DUE", s.getReviewDueAt().toLocalDate());
        }
        if (brand != null && brand.getStatus() == PayoutStatus.PAID && brand.getPaidAt() != null) {
            return new SellerSettlementDto.Schedule("PAID", brand.getPaidAt().toLocalDate());
        }
        if (brand != null && brand.getDueDate() != null && (brand.getStatus() == PayoutStatus.SCHEDULED
                || brand.getStatus() == PayoutStatus.REQUESTED)) {
            return new SellerSettlementDto.Schedule("PAYOUT_DUE", brand.getDueDate());
        }
        return new SellerSettlementDto.Schedule("NONE", null);
    }

    // ------------------------------------------------------------------ 요약(2-3)

    public SellerSettlementDto.Summary getSummary(String sellerEmail) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        LocalDateTime now = LocalDateTime.now();
        Map<SettlementStatus, Long> counts = SettlementPartyViews.partyStatusCounts(
                settlementRepository.countByStatusForMarket(market.getId()));
        long paidCount = 0, paidAmount = 0, scheduledCount = 0, scheduledAmount = 0;
        LocalDate nextPayoutDate = null;
        for (Object[] row : payoutRepository.aggregateForMarket(market.getId(), SettlementPayee.BRAND)) {
            PayoutStatus status = (PayoutStatus) row[0];
            long count = ((Number) row[1]).longValue();
            long amount = ((Number) row[2]).longValue();
            if (status == PayoutStatus.PAID) {
                paidCount += count;
                paidAmount += amount;
            } else if (status == PayoutStatus.SCHEDULED || status == PayoutStatus.REQUESTED) {
                scheduledCount += count;
                scheduledAmount += amount;
                LocalDate due = (LocalDate) row[3];
                if (due != null && (nextPayoutDate == null || due.isBefore(nextPayoutDate))) {
                    nextPayoutDate = due;
                }
            }
        }
        Object[] review = first(settlementRepository.reviewWindowForMarket(market.getId(), now));
        long reviewing = review == null ? 0 : ((Number) review[0]).longValue();
        LocalDateTime reviewingDueAt = review == null ? null : (LocalDateTime) review[1];
        return new SellerSettlementDto.Summary(SettlementPartyDto.Kpi.of(paidAmount, paidCount),
                SettlementPartyDto.Kpi.of(scheduledAmount, scheduledCount), nextPayoutDate,
                settlementRepository.existsScheduledWithBrandClawback(market.getId()),
                counts.get(SettlementStatus.REVIEWING), reviewingDueAt, counts.get(SettlementStatus.ADJUSTING),
                new SettlementPartyDto.StatusCounts(counts.get(SettlementStatus.REVIEWING),
                        counts.get(SettlementStatus.ADJUSTING), counts.get(SettlementStatus.PAYOUT_SCHEDULED),
                        counts.get(SettlementStatus.PAID)),
                reviewing);
    }

    // ------------------------------------------------------------------ 상세(2-4)

    public SellerSettlementDto.Detail getDetail(String sellerEmail, Long settlementId) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Settlement s = accessGuard.loadOwned(settlementId, market);
        LocalDateTime now = LocalDateTime.now();
        Map<SettlementPayee, SettlementPayout> payouts = views.payoutsOf(s.getId());
        SettlementPayout brand = payouts.get(SettlementPayee.BRAND);
        boolean confirmed = s.getStatus().isConfirmed();
        SettlementStatus status = s.getStatus().toPartyStatus();
        SettlementPartyDto.AdjustmentBlock adjustment = blocks.adjustmentOf(s);

        Page<SettlementItem> preview = itemRepository.findPageBySettlementId(s.getId(), PageRequest.of(0, PREVIEW_SIZE));
        List<SellerSettlementDto.TaxDocument> taxDocuments = confirmed ? blocks.taxDocumentsOf(s) : null;
        boolean canRequest = s.isInReviewWindow(now) && adjustment == null;
        return new SellerSettlementDto.Detail(s.getId(), s.getSettlementNumber(),
                new SellerSettlementDto.GroupBuyRef(s.getGroupBuyId(), s.getContract().getTitle()), influencer(s),
                s.getPeriodStartAt(), s.getPeriodEndAt(), status, status.getLabel(), status.getTone(),
                new SellerSettlementDto.Dates(s.getCreatedAt(), s.getOrdersClosedAt(), s.getReviewDueAt(),
                        s.getConfirmedAt(), s.getConfirmReason(),
                        s.getConfirmReason() == null ? null : s.getConfirmReason().getLabel(),
                        brand == null ? null : brand.getDueDate(),
                        brand == null || brand.getStatus() != PayoutStatus.PAID ? null : brand.getPaidAt(),
                        views.payoutBasis(s.getId())),
                adjustment, breakdown(s), confirmed ? payouts(s, payouts) : null,
                confirmed ? payment(s, brand) : null, taxDocuments, influencerTax(s), claimShipping(s, market),
                preview.getContent().stream().map(SellerSettlementQueryService::item).toList(),
                preview.getTotalElements(),
                new SellerSettlementDto.Actions(canRequest, blocks.canRespond(adjustment), confirmed,
                        taxDocuments != null && taxDocuments.stream().anyMatch(SellerSettlementDto.TaxDocument::downloadable)));
    }

    private SellerSettlementDto.Breakdown breakdown(Settlement s) {
        return new SellerSettlementDto.Breakdown(s.getGrossOrderAmount(),
                new SettlementPartyDto.AmountCount(s.getCancelDeduction(), s.getCancelCount()),
                new SettlementPartyDto.AmountCount(s.getReturnDeduction(), s.getReturnCount()),
                new SettlementPartyDto.AmountCount(s.getDeliveryExceptionDeduction(), s.getDeliveryExceptionCount()),
                s.getConfirmedSalesAmount(),
                new SellerSettlementDto.RateAmount(s.getPgFeeRate(), s.getPgFeeAmount()),
                new SellerSettlementDto.PlatformFee(s.getPlatformFeeRate(), s.getPlatformFeeAmount(),
                        properties.getPlatformFeeNormalRate()),
                s.getOriginalRewardAmount(), s.getRewardAmount(),
                new SellerSettlementDto.RateAmount(s.getRewardVatRate(), s.getRewardVatAmount()),
                new SettlementPartyDto.AmountCount(s.getReshipFeeAmount(), s.getReshipCount()),
                s.getConsumerDeliveryFeeAmount(), s.getBrandPayoutBeforeClawback(), blocks.clawbacksOf(s),
                s.getBrandClawbackAmount(), s.getBrandPayoutAmount());
    }

    /** 3자 분배 — 브랜드 첫 행. 남의 행의 분배 실패는 「지급 예정」으로 접는다 · 브랜드 행 실패만 「지급 확인 중」(0-4 · §46 A-4). */
    private List<SellerSettlementDto.Payout> payouts(Settlement s, Map<SettlementPayee, SettlementPayout> payouts) {
        String withholdingRate = SettlementPartyViews.percent(
                s.getWithholdingIncomeRate().add(s.getWithholdingLocalRate()).movePointRight(2));
        return List.of(SettlementPayee.BRAND, SettlementPayee.CREATOR, SettlementPayee.PLATFORM).stream()
                .filter(payouts::containsKey)
                .map(payee -> {
                    SettlementPayout payout = payouts.get(payee);
                    boolean mine = payee == SettlementPayee.BRAND;
                    PayoutStatus shown = !mine && payout.getStatus() == PayoutStatus.FAILED
                            ? PayoutStatus.SCHEDULED : payout.getStatus();
                    String label = switch (payee) {
                        case BRAND -> "우리";
                        case CREATOR -> s.getCreator().getShowroomName();
                        case PLATFORM -> "플랫폼";
                    };
                    String note = switch (payee) {
                        case BRAND -> null;
                        case CREATOR -> s.isBusinessCreator()
                                ? "리워드 %,d + 부가세 %,d".formatted(s.getRewardAfterClawback(), s.getCreatorVatAmount())
                                : "리워드 %,d − 원천징수 %,d(%s)".formatted(s.getRewardAfterClawback(),
                                        s.getWithholdingAmount(), withholdingRate);
                        case PLATFORM -> s.isBusinessCreator() ? "부가세는 인플루언서에게 지급" : "리워드 부가세";
                    };
                    return new SellerSettlementDto.Payout(payee, label, payout.getAmount(), note, shown,
                            statusLabel(s, payee, shown), payout.getDueDate(),
                            payout.getStatus() == PayoutStatus.PAID ? payout.getPaidAt() : null);
                }).toList();
    }

    private static String statusLabel(Settlement s, SettlementPayee payee, PayoutStatus status) {
        return switch (status) {
            case FAILED -> "지급 확인 중";
            case BLOCKED -> payee == SettlementPayee.CREATOR && s.isBusinessCreator() ? "발행 확인 후 지급" : "지급 예정";
            case REQUESTED -> PayoutStatus.SCHEDULED.getLabel();
            default -> status.getLabel();
        };
    }

    /**
     * 지급 정보 — 브랜드 행 스냅샷(확정 때 고정 · 「확정 회차는 기존 계좌로 지급」 · 기본정보 §16-4)이 있으면 그것, 없으면(확정 때
     * 계좌 미등록) 현재 계좌.
     */
    private SellerSettlementDto.Payment payment(Settlement s, SettlementPayout brand) {
        if (brand != null && brand.hasAccountSnapshot()) {
            return new SellerSettlementDto.Payment(brand.getBankName(), views.maskedSnapshotAccount(brand),
                    brand.getAccountHolder(), brand.getDueDate(),
                    brand.getStatus() == PayoutStatus.PAID ? brand.getPaidAt() : null,
                    brand.getStatus() == PayoutStatus.PAID ? brand.getPgReference() : null);
        }
        Seller seller = s.getMarket().getSeller();
        return new SellerSettlementDto.Payment(seller == null ? null : seller.getBankName(),
                seller == null ? null : SettlementAccountMasker.mask(seller.getAccountNumber()),
                seller == null ? null : seller.getAccountHolder(), brand == null ? null : brand.getDueDate(), null, null);
    }

    private static SellerSettlementDto.InfluencerTax influencerTax(Settlement s) {
        WithholdingType type = WithholdingType.from(s.getCreatorBusinessType());
        String label = s.isBusinessCreator() ? "사업자 · 세금계산서 발행 · 플랫폼 처리"
                : "비사업자 · 원천징수 %s · 플랫폼 신고".formatted(SettlementPartyViews.percent(
                s.getWithholdingIncomeRate().add(s.getWithholdingLocalRate()).movePointRight(2)));
        return new SellerSettlementDto.InfluencerTax(s.getCreatorBusinessType(), type == null ? null : type.name(), label);
    }

    /** 반품 · 교환 배송비(1-1) — 조회 시 계산 · 정산 행에 저장하지 않는다. 브랜드 부담은 요금표 값 그대로(왕복 · 편도 [미정]). */
    private SellerSettlementDto.ClaimShipping claimShipping(Settlement s, Market market) {
        long consumerCount = 0, consumerAmount = 0, brandCount = 0, brandAmount = 0;
        for (Object[] row : claimCollectionRepository.sumFeesByGroupBuy(s.getGroupBuyId())) {
            ClaimFeeBearer bearer = (ClaimFeeBearer) row[0];
            ClaimType type = (ClaimType) row[1];
            long count = ((Number) row[2]).longValue();
            if (bearer == ClaimFeeBearer.CONSUMER) {
                consumerCount += count;
                consumerAmount += ((Number) row[3]).longValue();
            } else {
                brandCount += count;
                Integer fee = type == ClaimType.EXCHANGE ? market.getExchangeFee() : market.getReturnFee();
                brandAmount += count * (fee == null ? 0 : fee);
            }
        }
        return new SellerSettlementDto.ClaimShipping(
                new SettlementPartyDto.AmountCount(consumerAmount, Math.toIntExact(consumerCount)),
                new SettlementPartyDto.AmountCount(brandAmount, Math.toIntExact(brandCount)));
    }

    // ------------------------------------------------------------------ 명세(2-5)

    public PageResponse<SellerSettlementDto.Item> getItems(String sellerEmail, Long settlementId, PagingRequest paging) {
        Settlement s = accessGuard.loadOwned(settlementId, accessGuard.resolveMarket(sellerEmail));
        Page<SettlementItem> page = itemRepository.findPageBySettlementId(s.getId(), pageable(paging));
        return new PageResponse<>(page.getContent().stream().map(SellerSettlementQueryService::item).toList(), page);
    }

    /** SHOWROOMZ 발행 세금계산서 스트림(2-6) — 발행 완료 · 파일이 있을 때만(409) · 다른 정산의 문서는 404. */
    public SettlementFile downloadTaxDocument(String sellerEmail, Long settlementId, Long documentId) {
        Settlement s = accessGuard.loadOwned(settlementId, accessGuard.resolveMarket(sellerEmail));
        SettlementTaxDocument document = taxDocumentRepository.findById(documentId)
                .filter(d -> d.getSettlementId().equals(s.getId()) && d.getType().isBrandInvoice())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        if (document.getStatus() != TaxDocumentStatus.ISSUED || !document.hasFile()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_TAX_INVOICE_NOT_ISSUED);
        }
        String filename = document.getFileName() != null ? document.getFileName()
                : "세금계산서_%s.pdf".formatted(s.getSettlementNumber());
        return new SettlementFile(filename, SettlementFile.PDF, taxDocumentService.readFile(document));
    }

    public SettlementStatementExcel.File downloadItems(String sellerEmail, Long settlementId) {
        Settlement s = accessGuard.loadOwned(settlementId, accessGuard.resolveMarket(sellerEmail));
        SettlementStatementExcel.requireDownloadable(s);
        return statementExcel.write(s, itemRepository.findAllBySettlementId(s.getId()),
                SettlementStatementColumn.Surface.PARTNER);
    }

    static SellerSettlementDto.Item item(SettlementItem i) {
        return new SellerSettlementDto.Item(i.getOrderNumber(), i.getSubOrderNumber(), i.getConsumerNameMasked(),
                i.getProductName(), i.getOptionName(), i.getQuantity(), i.getSettledQuantity(), i.getUnitPrice(),
                i.getPaidAmount(), i.getStatus(), i.getStatus().getLabel(), i.getSettledAmount(), i.getRewardRate(),
                i.getRewardAmount());
    }

    // ------------------------------------------------------------------ 공통

    private static SellerSettlementDto.Influencer influencer(Settlement s) {
        return new SellerSettlementDto.Influencer(s.getCreatorId(), s.getCreator().getShowroomName());
    }

    static Pageable pageable(PagingRequest paging) {
        PagingRequest request = paging == null ? new PagingRequest() : paging;
        if (request.getSize() < 1 || request.getSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "size 는 1 ~ 100 이어야 합니다.");
        }
        return PageRequest.of(Math.max(0, request.getPage() - 1), request.getSize());
    }

    private static Object[] first(Collection<Object[]> rows) {
        return rows.isEmpty() ? null : rows.iterator().next();
    }
}
