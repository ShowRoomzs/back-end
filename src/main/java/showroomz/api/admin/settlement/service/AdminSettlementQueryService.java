package showroomz.api.admin.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.settlement.dto.AdminSettlementDto;
import showroomz.api.admin.settlement.type.AdminSettlementSort;
import showroomz.api.admin.settlement.type.AdminSettlementTab;
import showroomz.api.common.settlement.service.SettlementPartyViews;
import showroomz.domain.settlement.adjustment.entity.SettlementAdjustment;
import showroomz.domain.settlement.adjustment.repository.SettlementAdjustmentRepository;
import showroomz.domain.settlement.adjustment.service.SettlementAdjustmentReader;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementItem;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.repository.SettlementItemRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTotals;
import showroomz.domain.settlement.service.SettlementStatementExcel;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementAdminSort;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatementColumn;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 어드민 07a · 07b 조회(44 어드민 설계서 7-1 ~ 7-4 · 7-8 · 7-9) — 목록 · 탭 숫자 · 상세 · 명세 · 이슈 스레드 링크.
 *
 * <p>어드민은 분배 실패(PAYOUT_FAILED)를 접지 않는다 — 수취자 화면만 접는다(0-4). 증빙 · 차감 탭은 행 단위가 다르고
 * 원천(V181 · V182)이 붙기 전까지 빈 목록이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSettlementQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    /** 합계 행 — 지급 예정 · 지급 완료 · 분배 실패만(정산 확인 중 · 조정 협의 제외 · 07a 합계 행 규칙). */
    private static final Set<SettlementStatus> FOOTER_STATUSES = EnumSet.of(SettlementStatus.PAYOUT_SCHEDULED,
            SettlementStatus.PAID, SettlementStatus.PAYOUT_FAILED);

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementItemRepository itemRepository;
    private final SettlementAdjustmentRepository adjustmentRepository;
    private final SettlementAdjustmentReader adjustmentReader;
    private final SettlementPartyViews views;
    private final AdminSettlementDetailAssembler detailAssembler;
    private final SettlementStatementExcel statementExcel;

    // ------------------------------------------------------------------ 7-1 · 7-2 목록

    public AdminSettlementDto.ListResponse<?> list(AdminSettlementTab tab, String keyword, AdminSettlementSort sort,
                                                   PagingRequest paging) {
        AdminSettlementTab target = tab == null ? AdminSettlementTab.ALL : tab;
        Pageable pageable = pageable(paging);
        if (!target.isSettlementRow()) {
            // 증빙 · 차감 탭 — 행의 원천(V181 · V182)이 붙기 전이다.
            return new AdminSettlementDto.ListResponse<>(List.of(), new PaginationInfo(Page.empty(pageable)), null,
                    emptyToolbar(target));
        }
        SettlementAdminSort order = target.getFixedSort() != null ? target.getFixedSort()
                : (sort == null ? AdminSettlementSort.SCHEDULE_DESC : sort).getDomainSort();
        Page<Settlement> page = settlementRepository.searchForAdmin(target.getStatuses(), keyword, order, pageable);
        List<Long> ids = page.getContent().stream().map(Settlement::getId).toList();
        Map<Long, Map<SettlementPayee, SettlementPayout>> payouts = views.payoutsOf(ids);
        Map<Long, LocalDateTime> deadlines = ids.isEmpty() ? Map.of()
                : adjustmentRepository.findBySettlementIdIn(ids).stream()
                .collect(Collectors.toMap(SettlementAdjustment::getSettlementId, SettlementAdjustment::getDeadlineAt));
        List<AdminSettlementDto.ListItem> content = page.getContent().stream()
                .map(s -> item(s, payouts.getOrDefault(s.getId(), Map.of()), deadlines.get(s.getId())))
                .toList();
        return new AdminSettlementDto.ListResponse<>(content, new PaginationInfo(page),
                target == AdminSettlementTab.ALL ? footer(settlementRepository.totalsForAdmin(FOOTER_STATUSES, keyword))
                        : null,
                toolbar(target, keyword));
    }

    /** 7-3 — 탭 숫자 · GNB 배지(운영자 조치만 — 조정 협의 · 주민번호 미등록은 세지 않는다). */
    public AdminSettlementDto.Summary summary() {
        Map<SettlementStatus, Long> counts = new EnumMap<>(SettlementStatus.class);
        for (Object[] row : settlementRepository.countAllByStatus()) {
            counts.put((SettlementStatus) row[0], ((Number) row[1]).longValue());
        }
        long all = counts.values().stream().mapToLong(Long::longValue).sum();
        long failed = counts.getOrDefault(SettlementStatus.PAYOUT_FAILED, 0L);
        Map<String, Long> tabCounts = new LinkedHashMap<>();
        tabCounts.put(AdminSettlementTab.ALL.name(), all);
        tabCounts.put(AdminSettlementTab.REVIEWING.name(), counts.getOrDefault(SettlementStatus.REVIEWING, 0L));
        tabCounts.put(AdminSettlementTab.ADJUSTING.name(), counts.getOrDefault(SettlementStatus.ADJUSTING, 0L));
        tabCounts.put(AdminSettlementTab.PAYOUT_FAILED.name(), failed);
        tabCounts.put(AdminSettlementTab.EVIDENCE.name(), 0L);
        tabCounts.put(AdminSettlementTab.CLAWBACK.name(), 0L);
        return new AdminSettlementDto.Summary(tabCounts, failed);
    }

    // ------------------------------------------------------------------ 7-4 · 7-8 · 7-9

    public AdminSettlementDto.Detail detail(Long settlementId) {
        return detailAssembler.detail(load(settlementId));
    }

    public PageResponse<AdminSettlementDto.Item> items(Long settlementId, PagingRequest paging) {
        Settlement s = load(settlementId);
        Page<SettlementItem> page = itemRepository.findPageBySettlementId(s.getId(), pageable(paging));
        return new PageResponse<>(page.getContent().stream().map(AdminSettlementDetailAssembler::item).toList(), page);
    }

    public SettlementStatementExcel.File statement(Long settlementId) {
        Settlement s = load(settlementId);
        SettlementStatementExcel.requireDownloadable(s);
        return statementExcel.write(s, itemRepository.findAllBySettlementId(s.getId()),
                SettlementStatementColumn.Surface.ADMIN);
    }

    /** 20b 이슈 패널 보강 — 조정 스레드가 아니면 404. */
    public AdminSettlementDto.ByThread byThread(Long threadId) {
        SettlementAdjustmentReader.AdjustmentSummary adjustment = adjustmentReader.findByThreadId(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_ADJUSTMENT_NOT_FOUND));
        return detailAssembler.byThread(load(adjustment.settlementId()), adjustment);
    }

    // ------------------------------------------------------------------ 조립

    private Settlement load(Long settlementId) {
        return settlementRepository.findDetailById(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }

    private static AdminSettlementDto.ListItem item(Settlement s, Map<SettlementPayee, SettlementPayout> payouts,
                                                    LocalDateTime agreementDue) {
        LocalDateTime scheduleAt;
        String scheduleKind;
        switch (s.getStatus()) {
            case REVIEWING -> {
                scheduleAt = s.getReviewDueAt();
                scheduleKind = "REVIEW_DUE";
            }
            case ADJUSTING -> {
                scheduleAt = agreementDue;
                scheduleKind = "AGREEMENT_DUE";
            }
            case PAYOUT_SCHEDULED -> {
                scheduleAt = s.getPayoutDueDate() == null ? null : s.getPayoutDueDate().atStartOfDay();
                scheduleKind = "PAYOUT_DUE";
            }
            case PAID -> {
                scheduleAt = s.getPaidAt();
                scheduleKind = "PAID_AT";
            }
            default -> {
                scheduleAt = payouts.values().stream().filter(p -> p.getStatus() == PayoutStatus.FAILED)
                        .map(SettlementPayout::getFailedAt).filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo).orElse(null);
                scheduleKind = "FAILED_AT";
            }
        }
        return new AdminSettlementDto.ListItem(s.getId(), s.getSettlementNumber(), s.getGroupBuyId(),
                s.getContract().getTitle(), s.getMarketId(), s.getMarket().getMarketName(), s.getCreatorId(),
                s.getCreator().getShowroomName(), s.getCreatorBusinessType(), s.getPeriodStartAt(), s.getPeriodEndAt(),
                s.getConfirmedSalesAmount(), s.getBrandPayoutAmount(), s.getCreatorPayoutAmount(), s.getStatus(),
                s.getStatus().getLabel(), s.getStatus().getTone(), scheduleAt, scheduleKind);
    }

    private static AdminSettlementDto.Footer footer(SettlementTotals t) {
        return new AdminSettlementDto.Footer(t.count(), t.confirmedSalesAmount(), t.brandPayoutAmount(),
                t.pgFeeAmount(), t.creatorPayoutAmount(), t.withholdingAmount(), t.creatorVatAmount(),
                t.rewardVatAmount(), t.platformFeeAmount());
    }

    private AdminSettlementDto.Toolbar toolbar(AdminSettlementTab tab, String keyword) {
        return switch (tab) {
            case ADJUSTING -> new AdminSettlementDto.Toolbar(
                    settlementRepository.totalsForAdmin(EnumSet.of(SettlementStatus.ADJUSTING), keyword)
                            .confirmedSalesAmount(),
                    adjustmentRepository.findEarliestOpenDeadline(), null, null, null, null, null, null, null, null);
            case PAYOUT_FAILED -> failedToolbar();
            default -> null;
        };
    }

    /** 분배 실패 툴바 — 미지급 합계 · 「인플루언서 2 · 브랜드 1」 · 최장 경과일. */
    private AdminSettlementDto.Toolbar failedToolbar() {
        long amount = 0;
        LocalDateTime earliest = null;
        List<String> labels = new ArrayList<>();
        Map<SettlementPayee, Long> counts = new EnumMap<>(SettlementPayee.class);
        for (Object[] row : payoutRepository.aggregateFailed()) {
            counts.put((SettlementPayee) row[0], ((Number) row[1]).longValue());
            amount += ((Number) row[2]).longValue();
            LocalDateTime failedAt = (LocalDateTime) row[3];
            if (failedAt != null && (earliest == null || failedAt.isBefore(earliest))) {
                earliest = failedAt;
            }
        }
        counts.forEach((payee, count) -> labels.add(payee.getLabel() + " " + count));
        Long elapsed = earliest == null ? null : ChronoUnit.DAYS.between(earliest.toLocalDate(), LocalDate.now());
        return new AdminSettlementDto.Toolbar(null, null, amount, labels.isEmpty() ? null : String.join(" · ", labels),
                elapsed, null, null, null, null, null);
    }

    private static AdminSettlementDto.Toolbar emptyToolbar(AdminSettlementTab tab) {
        return tab == AdminSettlementTab.EVIDENCE
                ? new AdminSettlementDto.Toolbar(null, null, null, null, null, 0L, 0L, 0L, null, null)
                : new AdminSettlementDto.Toolbar(null, null, null, null, null, 0L, null, null, 0L, 0L);
    }

    private static Pageable pageable(PagingRequest paging) {
        PagingRequest request = paging == null ? new PagingRequest() : paging;
        if (request.getSize() < 1 || request.getSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "size 는 1 ~ 100 이어야 합니다.");
        }
        return PageRequest.of(Math.max(0, request.getPage() - 1), request.getSize());
    }
}
