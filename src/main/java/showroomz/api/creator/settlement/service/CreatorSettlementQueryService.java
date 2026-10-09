package showroomz.api.creator.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.common.settlement.dto.SettlementPartyDto;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.api.creator.settlement.dto.CreatorSettlementDto;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.service.SettlementCalculator;
import showroomz.domain.settlement.service.SettlementSchedule;
import showroomz.domain.settlement.service.SettlementStatementExcel;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementPartySort;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatementColumn;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.SettlementAccountMasker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 쇼룸 스튜디오 정산 관리 조회(44 스튜디오 설계서 2절) — 같은 정산 행을 인플루언서 창으로 읽는다. 날짜는 <b>내 행</b>(인플루언서 몫) 기준이다 —
 * 사업자는 브랜드와 지급 예정일이 다르다(정산의 {@code payout_due_date}를 직접 내리지 않는다 · 8-4 리스크).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorSettlementQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_SIZE = 5;
    private static final String PLATFORM_NAME = "SHOWROOMZ";

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementPartyViews views;
    private final SettlementCalculator calculator;
    private final SettlementStatementExcel statementExcel;
    private final SettlementProperties properties;
    private final CreatorSettlementBlocks blocks;

    // ------------------------------------------------------------------ 진입

    public Creator resolveCreator(String creatorEmail) {
        Users user = userRepository.findByUsername(creatorEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    /** 내 정산 — 남의 정산 · 없음은 404(31 설계 0-4 와 같은 이유로 403 을 쓰지 않는다). */
    public Settlement loadOwned(Long settlementId, Creator creator) {
        return settlementRepository.findDetailById(settlementId)
                .filter(settlement -> settlement.getCreatorId().equals(creator.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }

    // ------------------------------------------------------------------ 목록(2-2)

    public PageResponse<CreatorSettlementDto.ListItem> getSettlements(String creatorEmail,
                                                                       Set<SettlementStatus> statuses, String keyword,
                                                                       SettlementPartySort sort, PagingRequest paging) {
        Creator creator = resolveCreator(creatorEmail);
        if (statuses != null && statuses.contains(SettlementStatus.PAYOUT_FAILED)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PAYOUT_FAILED 는 필터로 받지 않습니다 — PAID 에 포함됩니다.");
        }
        Page<Settlement> page = settlementRepository.searchForCreator(creator.getId(),
                SettlementStatus.expandPartyFilter(statuses), keyword,
                sort == null ? SettlementPartySort.CREATED_DESC : sort, pageable(paging));
        List<Long> ids = page.getContent().stream().map(Settlement::getId).toList();
        Map<Long, Map<SettlementPayee, SettlementPayout>> payouts = views.payoutsOf(ids);
        Map<Long, List<SettlementPartyViews.RewardRate>> rates = views.rewardRatesOf(ids);
        return new PageResponse<>(page.getContent().stream().map(s -> {
            SettlementPayout mine = payouts.getOrDefault(s.getId(), Map.of()).get(SettlementPayee.CREATOR);
            SettlementStatus status = s.getStatus().toPartyStatus();
            LocalDate payoutDate = null;
            String payoutDateKind = null;
            if (mine != null && mine.getStatus() == PayoutStatus.PAID && mine.getPaidAt() != null) {
                payoutDate = mine.getPaidAt().toLocalDate();
                payoutDateKind = "PAID";
            } else if (mine != null && mine.getDueDate() != null && (mine.getStatus() == PayoutStatus.SCHEDULED
                    || mine.getStatus() == PayoutStatus.REQUESTED)) {
                payoutDate = mine.getDueDate();
                payoutDateKind = "SCHEDULED";
            }
            return new CreatorSettlementDto.ListItem(s.getId(), s.getSettlementNumber(), s.getGroupBuyId(),
                    s.getContract().getTitle(), s.getMarket().getMarketName(), s.getPeriodStartAt(),
                    s.getPeriodEndAt(), s.getConfirmedSalesAmount(),
                    SettlementPartyViews.rewardRateLabel(rates.get(s.getId())), s.getRewardAmount(),
                    s.getRewardClawbackAmount(), s.getWithholdingAmount(), s.getCreatorVatAmount(),
                    s.getCreatorPayoutAmount(), status, status.getLabel(), status.getTone(), payoutDate,
                    payoutDateKind);
        }).toList(), page);
    }

    // ------------------------------------------------------------------ 요약(2-3)

    public CreatorSettlementDto.Summary getSummary(String creatorEmail) {
        Creator creator = resolveCreator(creatorEmail);
        LocalDateTime now = LocalDateTime.now();
        Map<SettlementStatus, Long> counts = SettlementPartyViews.partyStatusCounts(
                settlementRepository.countByStatusForCreator(creator.getId()));
        long paidCount = 0, paidAmount = 0, scheduledCount = 0, scheduledAmount = 0;
        LocalDate nearest = null;
        for (Object[] row : payoutRepository.aggregateForCreator(creator.getId(), SettlementPayee.CREATOR)) {
            PayoutStatus status = (PayoutStatus) row[0];
            long count = ((Number) row[1]).longValue();
            long amount = ((Number) row[2]).longValue();
            switch (status) {
                case PAID -> {
                    paidCount += count;
                    paidAmount += amount;
                }
                case SCHEDULED, REQUESTED, BLOCKED -> {
                    scheduledCount += count;
                    scheduledAmount += amount;
                    LocalDate due = (LocalDate) row[3];
                    if (status != PayoutStatus.BLOCKED && due != null && (nearest == null || due.isBefore(nearest))) {
                        nearest = due;
                    }
                }
                default -> {
                }
            }
        }
        List<Object[]> reviewRows = settlementRepository.reviewWindowForCreator(creator.getId(), now);
        Object[] review = reviewRows.isEmpty() ? null : reviewRows.get(0);
        long openReview = review == null ? 0 : ((Number) review[0]).longValue();
        LocalDateTime nearestDueAt = review == null ? null : (LocalDateTime) review[1];
        return new CreatorSettlementDto.Summary(SettlementPartyDto.Kpi.of(paidAmount, paidCount),
                new CreatorSettlementDto.ScheduledKpi(scheduledCount == 0 ? null : scheduledAmount, scheduledCount,
                        nearest),
                new CreatorSettlementDto.ReviewingKpi(counts.get(SettlementStatus.REVIEWING), nearestDueAt),
                new CreatorSettlementDto.CountKpi(counts.get(SettlementStatus.ADJUSTING)),
                new SettlementPartyDto.StatusCounts(counts.get(SettlementStatus.REVIEWING),
                        counts.get(SettlementStatus.ADJUSTING), counts.get(SettlementStatus.PAYOUT_SCHEDULED),
                        counts.get(SettlementStatus.PAID)),
                openReview + blocks.taxInvoiceAttentionCount(creator.getId()));
    }

    // ------------------------------------------------------------------ 상세(2-4)

    public CreatorSettlementDto.Detail getDetail(String creatorEmail, Long settlementId) {
        Creator me = resolveCreator(creatorEmail);
        Settlement s = loadOwned(settlementId, me);
        LocalDateTime now = LocalDateTime.now();
        boolean confirmed = s.getStatus().isConfirmed();
        SettlementStatus status = s.getStatus().toPartyStatus();
        Map<SettlementPayee, SettlementPayout> payouts = views.payoutsOf(s.getId());
        SettlementPayout mine = payouts.get(SettlementPayee.CREATOR);
        SettlementPartyDto.AdjustmentBlock adjustment = blocks.adjustmentOf(s);
        List<SettlementPartyViews.RewardRate> rates = views.rewardRatesOf(List.of(s.getId())).get(s.getId());

        LocalDate payoutDueDate = null;
        String payoutDueNote = null;
        if (confirmed && mine != null) {
            if (mine.getStatus() == PayoutStatus.BLOCKED) {
                payoutDueNote = s.isBusinessCreator()
                        ? blocks.businessBlockedNote(s, properties.getPayoutBusinessDays()) : "주민등록번호 등록 후 지급";
            } else {
                payoutDueDate = mine.getDueDate();
                payoutDueNote = views.payoutBasis(s.getId());
            }
        }
        LocalDateTime paidAt = mine != null && mine.getStatus() == PayoutStatus.PAID ? mine.getPaidAt() : null;

        CreatorSettlementDto.Review review = s.getStatus() != SettlementStatus.REVIEWING ? null
                : new CreatorSettlementDto.Review(s.isInReviewWindow(now) && adjustment == null, s.getReviewDueAt(),
                calculator.maxRewardAmount(s));
        CreatorSettlementDto.Withholding withholding = s.isBusinessCreator() ? null
                : new CreatorSettlementDto.Withholding(PLATFORM_NAME, s.getWithholdingAmount(),
                status == SettlementStatus.PAID && blocks.receiptAvailable(s),
                paidAt != null ? "징수액" : "징수 예정액");

        Page<SettlementItem> preview = itemRepository.findPageBySettlementId(s.getId(), PageRequest.of(0, PREVIEW_SIZE));
        return new CreatorSettlementDto.Detail(
                new CreatorSettlementDto.SettlementHeader(s.getId(), s.getSettlementNumber(), s.getGroupBuyId(),
                        s.getContract().getTitle(), s.getMarket().getMarketName(), s.getMarketId(),
                        s.getPeriodStartAt(), s.getPeriodEndAt(), status, status.getLabel(), status.getTone(),
                        s.getCreatorBusinessType(), s.isBusinessCreator()),
                new CreatorSettlementDto.Timeline(s.getCreatedAt(), s.getOrdersClosedAt(), s.getReviewDueAt(),
                        s.getConfirmedAt(), s.getConfirmReason(),
                        s.getConfirmReason() == null ? null : s.getConfirmReason().getLabel(), payoutDueDate,
                        payoutDueNote, paidAt),
                review, breakdown(s, rates), blocks.clawbacksOf(s),
                new CreatorSettlementDto.Payouts(!confirmed, payoutRows(s, payouts)),
                payment(s, mine, payoutDueDate, payoutDueNote), adjustment, withholding,
                s.isBusinessCreator() && confirmed ? blocks.taxInvoiceOf(s) : null,
                new CreatorSettlementDto.Items(preview.getContent().stream().map(CreatorSettlementQueryService::item)
                        .toList(), preview.getTotalElements(), confirmed));
    }

    private static CreatorSettlementDto.Breakdown breakdown(Settlement s, List<SettlementPartyViews.RewardRate> rates) {
        return new CreatorSettlementDto.Breakdown(s.getGrossOrderAmount(),
                new SettlementPartyDto.AmountCount(s.getCancelDeduction(), s.getCancelCount()),
                new SettlementPartyDto.AmountCount(s.getReturnDeduction(), s.getReturnCount()),
                new SettlementPartyDto.AmountCount(s.getDeliveryExceptionDeduction(), s.getDeliveryExceptionCount()),
                s.getConfirmedSalesAmount(), SettlementPartyViews.rewardRateLabel(rates),
                SettlementPartyViews.distinctByProduct(rates).stream()
                        .map(rate -> new CreatorSettlementDto.ProductRate(rate.productName(), rate.rate())).toList(),
                s.getOriginalRewardAmount(), s.getRewardAmount(), s.getRewardClawbackAmount(),
                s.getRewardAfterClawback(), s.getWithholdingIncomeRate(), s.getWithholdingLocalRate(),
                s.getWithholdingAmount(), s.getRewardVatRate(), s.getCreatorVatAmount(), s.getCreatorPayoutAmount());
    }

    /** 3자 분배 — 내 행 첫 줄. 내 행 실패는 「지급 확인 중」 · 브랜드 행 실패는 「지급 예정」으로 접는다(어드민 설계서 0-4 · §46 A-4). */
    private List<CreatorSettlementDto.PayoutRow> payoutRows(Settlement s, Map<SettlementPayee, SettlementPayout> payouts) {
        String withholdingRate = SettlementPartyViews.percent(
                s.getWithholdingIncomeRate().add(s.getWithholdingLocalRate()).movePointRight(2));
        return List.of(SettlementPayee.CREATOR, SettlementPayee.BRAND, SettlementPayee.PLATFORM).stream()
                .filter(payouts::containsKey)
                .map(payee -> {
                    SettlementPayout payout = payouts.get(payee);
                    boolean mine = payee == SettlementPayee.CREATOR;
                    PayoutStatus shown = !mine && payout.getStatus() == PayoutStatus.FAILED
                            ? PayoutStatus.SCHEDULED : payout.getStatus();
                    String name = switch (payee) {
                        case CREATOR -> s.getCreator().getShowroomName();
                        case BRAND -> s.getMarket().getMarketName();
                        case PLATFORM -> PLATFORM_NAME;
                    };
                    String note = switch (payee) {
                        case CREATOR -> s.isBusinessCreator()
                                ? "리워드 %,d + 부가세 %,d".formatted(s.getRewardAfterClawback(), s.getCreatorVatAmount())
                                : "리워드 %,d − 원천징수 %,d(%s)".formatted(s.getRewardAfterClawback(),
                                        s.getWithholdingAmount(), withholdingRate);
                        case BRAND -> null;
                        case PLATFORM -> s.isBusinessCreator() ? "부가세는 인플루언서에게 지급" : "플랫폼 신고 · 납부";
                    };
                    LocalDate date = shown == PayoutStatus.PAID && payout.getPaidAt() != null
                            ? payout.getPaidAt().toLocalDate() : payout.getDueDate();
                    return new CreatorSettlementDto.PayoutRow(payee, payee.getLabel(), name, payout.getAmount(), note,
                            shown, statusLabel(s, payee, shown, date), date);
                }).toList();
    }

    private String statusLabel(Settlement s, SettlementPayee payee, PayoutStatus status, LocalDate date) {
        String day = date == null ? "" : date.format(SettlementSchedule.DAY) + " ";
        return switch (status) {
            case WAITING -> "확인 기간 후 지급";
            case HELD -> "보류 중";
            case BLOCKED -> payee == SettlementPayee.CREATOR && s.isBusinessCreator()
                    ? blocks.businessBlockedLabel(s) : "주민등록번호 등록 필요";
            case SCHEDULED, REQUESTED -> day + "지급 예정";
            case PAID -> day + "지급 완료";
            case FAILED -> "지급 확인 중";
            case NOT_APPLICABLE -> "—";
        };
    }

    /** 입금 계좌 — 지시 후엔 내 행 스냅샷, 지시 전엔 현재 계좌 · 예금주는 실명. 계좌가 비어 있으면(온보딩 미완료) null. */
    private CreatorSettlementDto.Payment payment(Settlement s, SettlementPayout mine, LocalDate dueDate, String dueNote) {
        boolean paid = mine != null && mine.getStatus() == PayoutStatus.PAID;
        if (mine != null && mine.hasAccountSnapshot()) {
            return new CreatorSettlementDto.Payment(mine.getBankName(), views.maskedSnapshotAccount(mine),
                    mine.getAccountHolder(), dueDate, dueNote, paid ? mine.getPaidAt() : null,
                    paid ? mine.getPgReference() : null);
        }
        Creator creator = s.getCreator();
        if (creator.getAccountNumber() == null || creator.getAccountNumber().isBlank()) {
            return null;
        }
        return new CreatorSettlementDto.Payment(creator.getBankName(),
                SettlementAccountMasker.mask(creator.getAccountNumber()), creator.getRealName(), dueDate, dueNote,
                null, null);
    }

    // ------------------------------------------------------------------ 명세(2-5 · 4-1)

    public PageResponse<CreatorSettlementDto.Item> getItems(String creatorEmail, Long settlementId,
                                                            PagingRequest paging) {
        Settlement s = loadOwned(settlementId, resolveCreator(creatorEmail));
        Page<SettlementItem> page = itemRepository.findPageBySettlementId(s.getId(), pageable(paging));
        return new PageResponse<>(page.getContent().stream().map(CreatorSettlementQueryService::item).toList(), page);
    }

    public SettlementStatementExcel.File downloadItems(String creatorEmail, Long settlementId) {
        Settlement s = loadOwned(settlementId, resolveCreator(creatorEmail));
        SettlementStatementExcel.requireDownloadable(s);
        return statementExcel.write(s, itemRepository.findAllBySettlementId(s.getId()),
                SettlementStatementColumn.Surface.STUDIO);
    }

    private static CreatorSettlementDto.Item item(SettlementItem i) {
        return new CreatorSettlementDto.Item(i.getOrderNumber(), i.getProductName(), i.getOptionName(),
                i.getQuantity(), i.getSettledQuantity(), i.getPaidAmount(), i.getStatus(), i.getStatus().getLabel(),
                i.getSettledAmount(), i.getRewardRate(), i.getRewardAmount());
    }

    private static Pageable pageable(PagingRequest paging) {
        PagingRequest request = paging == null ? new PagingRequest() : paging;
        if (request.getSize() < 1 || request.getSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "size 는 1 ~ 100 이어야 합니다.");
        }
        return PageRequest.of(Math.max(0, request.getPage() - 1), request.getSize());
    }
}
