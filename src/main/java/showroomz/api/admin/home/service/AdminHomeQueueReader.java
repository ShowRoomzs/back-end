package showroomz.api.admin.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.api.admin.changerequest.service.AdminChangeRequestService;
import showroomz.api.admin.contract.service.AdminContractQueryService;
import showroomz.api.admin.groupbuy.service.AdminGroupBuyQueryService;
import showroomz.api.admin.home.dto.AdminHomeDto;
import showroomz.api.admin.home.type.TodoUrgency;
import showroomz.api.admin.inquiry.service.AdminInquiryService;
import showroomz.api.admin.post.service.AdminPostQueryService;
import showroomz.api.admin.productinquiry.service.AdminProductInquiryService;
import showroomz.api.admin.settlement.service.AdminClawbackRows;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.service.AdminOrderExceptionService;
import showroomz.api.admin.transaction.service.AdminRefundService;
import showroomz.domain.contract.type.AdminContractQueue;
import showroomz.domain.groupbuy.type.AdminGroupBuyQueue;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.support.OneToOneInquiryNumber;
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.metrics.repository.AdminOperationsQueryRepository;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.config.properties.AdminProperties;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어드민 운영 큐(1017 설계서 6-1) — 홈 할 일과 사이드바 배지가 <b>같은 큐 계산</b>을 쓴다(「할 일 합계 = 사이드바 배지 합」).
 * 큐 건수는 각 모듈 요약(환불 · 예외 · 계약 · 공구 · 문의 · 변경 요청 · 게시물 · 정산 증빙)과 같은 값이고, 여기서는 건수를 급한 정도별 행으로
 * 나누기만 한다 — 실패 · 기한 초과 · 집행 대기 · 기한 임박은 건별 행, 나머지는 큐별 대기 행 하나.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHomeQueueReader {

    /** 건별 행 상한 — 넘치면 같은 급한 정도의 「외 N건」 행으로 접는다. */
    static final int ROW_LIMIT = 20;
    static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM.dd");

    static final String ONBOARDING = "ONBOARDING";
    static final String MEMBERS = "MEMBERS";
    static final String CONTRACTS = "CONTRACTS";
    static final String GROUP_BUYS = "GROUP_BUYS";
    static final String TRANSACTIONS = "TRANSACTIONS";
    static final String SETTLEMENTS = "SETTLEMENTS";
    static final String CS = "CS";
    static final String POSTS = "POSTS";

    private final AdminRefundService refundService;
    private final AdminOrderExceptionService exceptionService;
    private final AdminContractQueryService contractQueryService;
    private final AdminGroupBuyQueryService groupBuyQueryService;
    private final AdminInquiryService inquiryService;
    private final AdminChangeRequestService changeRequestService;
    private final AdminProductInquiryService productInquiryService;
    private final AdminPostQueryService postQueryService;
    private final AdminClawbackRows clawbackRows;
    private final SettlementRepository settlementRepository;
    private final SettlementTaxDocumentRepository taxDocumentRepository;
    private final OrderClaimRepository claimRepository;
    private final BrandWithdrawalRequestRepository brandWithdrawalRepository;
    private final OneToOneInquiryRepository inquiryRepository;
    private final AdminOperationsQueryRepository opsRepository;
    private final BusinessCalendar businessCalendar;
    private final AdminProperties adminProperties;
    private final GroupBuyProperties groupBuyProperties;

    public Snapshot read(LocalDateTime now) {
        List<Queue> queues = new ArrayList<>();
        AdminTransactionDto.RefundSummary refund = refundService.getSummary(null);
        Map<AdminGroupBuyQueue, Long> groupBuyQueues = groupBuyQueryService.getSummary().queues();

        queues.add(failedRefunds(tabCount(refund, "FAILED"), now));
        queues.add(failedPayouts(now));
        queues.add(inquiries(now));
        queues.add(transferRefunds(refund.transferPending(), now));
        queues.add(operatorRefunds(Math.max(0, tabCount(refund, "PENDING") - refund.transferPending()), now));
        queues.add(openReviews(groupBuyQueues.getOrDefault(AdminGroupBuyQueue.OPEN_REVIEW, 0L), now));
        queues.addAll(onboarding(now));
        queues.addAll(taxDocuments(now));
        AdminChangeRequestDto.SummaryResponse changeRequests = changeRequestService.getSummary();
        queues.add(waiting("CHANGE_REQUEST", ONBOARDING, "변경 요청 검토", changeRequestDetail(changeRequests),
                changeRequests.getPendingCount(), opsRepository.oldestPendingChangeRequest(),
                "/change-requests?status=PENDING", now));
        queues.add(waiting("WITHDRAWAL", MEMBERS, "탈퇴 승인 검토", null,
                brandWithdrawalRepository.countPending() + opsRepository.countPendingCreatorWithdrawals(),
                opsRepository.oldestPendingWithdrawal(), "/members/withdrawals", now));
        AdminClawbackRows.Rows clawbacks = clawbackRows.build(null);
        queues.add(waiting("CLAWBACK_UNRECOVERABLE", SETTLEMENTS, "차감 미회수", clawbackDetail(clawbacks),
                clawbacks.unrecoverableCount(), opsRepository.oldestUnrecoverableClawback(),
                "/settlements?tab=CLAWBACK", now));
        // 경과(56 E1) — 처리 지연 · 배송 예외는 예외 관리 목록 「경과」 열과 같은 기준 시각(처리 지연 = 기한 · 배송 예외 = 유형별 기준 시각).
        AdminOrderExceptionService.SummaryWithOldest exceptionSnapshot = exceptionService.getSummaryWithOldest();
        AdminOrderExceptionDto.ExceptionSummary exceptions = exceptionSnapshot.summary();
        queues.add(waiting("PROCESSING_DELAY", TRANSACTIONS, "처리 지연 확인",
                kindDetail(exceptions, AdminOrderExceptionDto.ExceptionTab.DELAY),
                exceptions.tabCounts().getOrDefault("DELAY", 0L),
                exceptionSnapshot.oldest(AdminOrderExceptionDto.ExceptionTab.DELAY), "/order-exceptions?tab=DELAY", now));
        // 사이드바 배지 범위(order.exception.badge-scope)가 처리 지연만이면 배송 예외는 할 일에 넣지 않는다 — 합계가 배지와 같아야 한다.
        queues.add(waiting("DELIVERY_EXCEPTION", TRANSACTIONS, "배송 예외 확인",
                kindDetail(exceptions, AdminOrderExceptionDto.ExceptionTab.DELIVERY),
                "ALL".equals(exceptions.badgeScope()) ? exceptions.tabCounts().getOrDefault("DELIVERY", 0L) : 0L,
                exceptionSnapshot.oldest(AdminOrderExceptionDto.ExceptionTab.DELIVERY), "/order-exceptions?tab=DELIVERY",
                now));
        queues.add(waiting("CLAIM_DISPUTE", TRANSACTIONS, "반려 이의 확인", null, claimRepository.countOpenDisputes(null),
                opsRepository.oldestOpenDispute(), "/claims?dispute=OPEN", now));
        long postActions = postQueryService.getSummary().getActionRequired();
        queues.add(waiting("POST_REPORT", POSTS, "신고 게시물 검토", "일반 게시물 신고 누적", postActions,
                postActions == 0 ? null : postQueryService.oldestActionRequiredSince(), "/posts?tab=REPORTED", now));
        long deleteRequests = productInquiryService.getSummary().getDeleteRequestedCount();
        queues.add(waiting("PRODUCT_INQUIRY_DELETE", CS, "상품 문의 삭제 요청", null, deleteRequests,
                deleteRequests == 0 ? null : opsRepository.oldestProductInquiryDeleteRequest(),
                "/product-inquiries?status=DELETE_REQUESTED", now));
        queues.addAll(contracts(now));
        queues.add(waiting("SUSPEND_REQUEST", GROUP_BUYS, "중단 요청", null,
                groupBuyQueues.getOrDefault(AdminGroupBuyQueue.SUSPEND_REQUEST, 0L),
                opsRepository.oldestPendingGroupBuyChangeRequest(ChangeRequestType.SUSPEND),
                "/group-buys?queue=SUSPEND_REQUEST", now));
        queues.add(waiting("EARLY_CLOSE_REQUEST", GROUP_BUYS, "조기 마감 요청", null,
                groupBuyQueues.getOrDefault(AdminGroupBuyQueue.EARLY_CLOSE_REQUEST, 0L),
                opsRepository.oldestPendingGroupBuyChangeRequest(ChangeRequestType.EARLY_CLOSE),
                "/group-buys?queue=EARLY_CLOSE_REQUEST", now));
        long appealReviews = groupBuyQueues.getOrDefault(AdminGroupBuyQueue.APPEAL_REVIEW, 0L);
        queues.add(waiting("APPEAL_REVIEW", GROUP_BUYS, "소명 검토", null, appealReviews,
                appealReviews == 0 ? null : opsRepository.oldestAppealReview(now),
                "/group-buys?queue=APPEAL_REVIEW", now));
        return new Snapshot(queues);
    }

    // ------------------------------------------------------------------ 대기 행 보조줄(55 계획서 A3 · 00-home rev.4)

    /** 「브랜드 2 · 인플루언서 2」 — 0인 쪽은 뺀다. */
    private static String changeRequestDetail(AdminChangeRequestDto.SummaryResponse summary) {
        List<String> parts = new ArrayList<>();
        if (summary.getBrandPendingCount() > 0) {
            parts.add("브랜드 " + summary.getBrandPendingCount());
        }
        if (summary.getCreatorPendingCount() > 0) {
            parts.add("인플루언서 " + summary.getCreatorPendingCount());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /** 1건이면 「CLW-0003 · 2,094원」, 여러 건이면 「N건 · 합계 X원」. */
    private static String clawbackDetail(AdminClawbackRows.Rows rows) {
        if (rows.unrecoverableCount() == 0) {
            return null;
        }
        if (rows.unrecoverableCount() == 1 && !rows.unrecoverableNumbers().isEmpty()) {
            return rows.unrecoverableNumbers().getFirst() + " · " + String.format("%,d원", rows.unrecoverableAmount());
        }
        return rows.unrecoverableCount() + "건 · 합계 " + String.format("%,d원", rows.unrecoverableAmount());
    }

    /** 예외 유형별 건수 — 그 탭의 0이 아닌 유형만, 많은 순(같으면 유형 순). 「추적 정지 2 · 집화 확인 필요 1 · 반송 중 1」. */
    private static String kindDetail(AdminOrderExceptionDto.ExceptionSummary summary, AdminOrderExceptionDto.ExceptionTab tab) {
        String detail = java.util.Arrays.stream(AdminOrderExceptionDto.ExceptionKind.values())
                .filter(kind -> kind.tab() == tab)
                .filter(kind -> summary.kindCounts().getOrDefault(kind.name(), 0L) > 0)
                .sorted(Comparator.comparingLong(
                        (AdminOrderExceptionDto.ExceptionKind kind) -> summary.kindCounts().get(kind.name())).reversed())
                .map(kind -> kind.getLabel() + " " + summary.kindCounts().get(kind.name()))
                .collect(Collectors.joining(" · "));
        return detail.isEmpty() ? null : detail;
    }

    // ------------------------------------------------------------------ 건별 — 실패 · 집행 대기

    private Queue failedRefunds(long count, LocalDateTime now) {
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        for (OrderRefundTask task : opsRepository.findFailedRefunds(ROW_LIMIT)) {
            LocalDateTime failedAt = task.getFailedAt() != null ? task.getFailedAt() : task.getModifiedAt();
            rows.add(row(TodoUrgency.FAILED, "실패", "REFUND_FAILED",
                    "환불 실패 · " + task.getOrder().getOrderNumber(),
                    join(task.getLastError(), won(task.getRefundAmount())), TRANSACTIONS, ageText(failedAt, now), null,
                    new AdminHomeDto.Target("REFUND", task.getId(), null), 1));
        }
        return itemized("REFUND_FAILED", TRANSACTIONS, count, rows, TodoUrgency.FAILED, "환불 실패", "/refunds?tab=FAILED");
    }

    private Queue failedPayouts(LocalDateTime now) {
        long count = 0;
        for (Object[] row : settlementRepository.countAllByStatus()) {
            if (row[0] == SettlementStatus.PAYOUT_FAILED) {
                count += ((Number) row[1]).longValue();
            }
        }
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        if (count > 0) {
            List<Object[]> failed = opsRepository.findPayoutFailedSettlements(ROW_LIMIT);
            Map<Long, List<SettlementPayout>> payouts = opsRepository.findFailedPayouts(
                            failed.stream().map(row -> ((Settlement) row[0]).getId()).toList()).stream()
                    .collect(Collectors.groupingBy(SettlementPayout::getSettlementId));
            for (Object[] row : failed) {
                Settlement settlement = (Settlement) row[0];
                rows.add(row(TodoUrgency.FAILED, "실패", "PAYOUT_FAILED", "분배 실패 · " + settlement.getSettlementNumber(),
                        payoutFailureDetail(payouts.get(settlement.getId()), settlement), SETTLEMENTS,
                        ageText((LocalDateTime) row[1], now), null,
                        new AdminHomeDto.Target("SETTLEMENT", settlement.getId(), null), 1));
            }
        }
        return itemized("PAYOUT_FAILED", SETTLEMENTS, count, rows, TodoUrgency.FAILED, "분배 실패",
                "/settlements?tab=PAYOUT_FAILED");
    }

    /**
     * 분배 실패 보조줄(1017 설계서 6-1 「수취자 · 금액」 · 56 E2) — 「인플루언서 몫 259,930원 반환 · 예금주 불일치」. 실패 행이 여럿이면 몫을
     * 잇고(실패가 오래된 순), 사유는 첫 실패 행(정산 상세 레일 {@code failedPayout}과 같은 행)의 것이다. 실패 행이 읽히지 않으면 계약 제목.
     */
    private static String payoutFailureDetail(List<SettlementPayout> payouts, Settlement settlement) {
        if (payouts == null || payouts.isEmpty()) {
            return settlement.getContract().getTitle();
        }
        List<SettlementPayout> ordered = payouts.stream()
                .sorted(Comparator.comparing(SettlementPayout::getFailedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SettlementPayout::getId))
                .toList();
        String shares = ordered.stream().map(p -> p.getPayee().getLabel() + " 몫 " + won(p.getAmount()))
                .collect(Collectors.joining(" · "));
        return join(shares + " 반환", ordered.getFirst().getFailReason());
    }

    private Queue operatorRefunds(long count, LocalDateTime now) {
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        if (count > 0) {
            for (OrderRefundTask task : opsRepository.findOperatorPendingRefunds(
                    showroomz.domain.order.type.RefundMethod.PG_CANCEL, ROW_LIMIT)) {
                // 보조줄(56 E2) — 「구매확정 후 하자 · 27,200원」. 사유는 편입 때 고른 운영자 사유 라벨.
                rows.add(row(TodoUrgency.EXECUTION_PENDING, "집행 대기", "REFUND_EXECUTION",
                        "사유 환불 집행 대기 · " + task.getOrder().getOrderNumber(),
                        join(task.getReasonCode() == null ? null : task.getReasonCode().getLabel(), won(task.getRefundAmount())),
                        TRANSACTIONS, ageText(task.getCreatedAt(), now), null,
                        new AdminHomeDto.Target("REFUND", task.getId(), null), 1));
            }
        }
        return itemized("REFUND_EXECUTION", TRANSACTIONS, count, rows, TodoUrgency.EXECUTION_PENDING, "사유 환불 집행 대기",
                "/refunds?tab=PENDING");
    }

    /** 송금 대기(1021 설계서 4-5 · 16절) — 원래 결제액을 넘는 운임 환급. 운영자가 계좌로 보낸 뒤 [송금 완료 기록]. */
    private Queue transferRefunds(long count, LocalDateTime now) {
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        if (count > 0) {
            for (OrderRefundTask task : opsRepository.findOperatorPendingRefunds(
                    showroomz.domain.order.type.RefundMethod.BANK_TRANSFER, ROW_LIMIT)) {
                rows.add(row(TodoUrgency.EXECUTION_PENDING, "송금 대기", "REFUND_TRANSFER",
                        "운임 환급 송금 대기 · " + task.getOrder().getOrderNumber(), won(task.getRefundAmount()),
                        TRANSACTIONS, ageText(task.getCreatedAt(), now), null,
                        new AdminHomeDto.Target("REFUND", task.getId(), null), 1));
            }
        }
        return itemized("REFUND_TRANSFER", TRANSACTIONS, count, rows, TodoUrgency.EXECUTION_PENDING, "운임 환급 송금 대기",
                "/refunds?tab=PENDING&route=FREIGHT");
    }

    // ------------------------------------------------------------------ 기한 있는 큐 — 기한 초과 · 임박은 건별, 나머지는 대기 1행

    /** 1:1 문의 — 접수 + SLA(24시간) 경과면 기한 초과. */
    private Queue inquiries(LocalDateTime now) {
        long count = inquiryService.getSummary().getUnansweredCount();
        LocalDateTime threshold = now.minusHours(adminProperties.getInquirySlaHours());
        long overdue = count == 0 ? 0 : opsRepository.countInquiriesWaitingBefore(threshold);
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        if (overdue > 0) {
            for (OneToOneInquiry inquiry : opsRepository.findInquiriesWaitingBefore(threshold, ROW_LIMIT)) {
                long overHours = Duration.between(inquiry.getCreatedAt(), threshold).toHours();
                // 보조줄(56 E2) — 「INQ-20261015-031 · 본문 앞부분」. 번호 규칙은 1:1 문의 관리와 같은 한 곳.
                rows.add(row(TodoUrgency.OVERDUE, "기한 초과", "INQUIRY",
                        "1:1 문의 · " + (inquiry.getType() == null ? "기타" : inquiry.getType().getDescription()),
                        join(OneToOneInquiryNumber.of(inquiry, inquiryRepository), snippet(inquiry.getContent())), CS,
                        ageText(inquiry.getCreatedAt(), now), "+" + overHours + "시간",
                        new AdminHomeDto.Target("INQUIRY", inquiry.getId(), null), 1));
            }
            fold(rows, overdue, TodoUrgency.OVERDUE, "INQUIRY", "1:1 문의 기한 초과", CS, "/inquiries?status=WAITING");
        }
        long inSla = Math.max(0, count - overdue);
        if (inSla > 0) {
            rows.add(row(TodoUrgency.WAITING, "대기", "INQUIRY", "1:1 문의(기한 내)",
                    overdue > 0 ? "기한 내 · 초과 " + overdue + "건은 위에" : "기한 내", CS,
                    ageText(opsRepository.oldestWaitingInquirySince(threshold), now), null,
                    new AdminHomeDto.Target("QUEUE", null, "/inquiries?status=WAITING"), inSla));
        }
        return new Queue("INQUIRY", CS, count, rows);
    }

    /** 오픈 승인 — 제출 + 3영업일 마감(공구 상세의 마감과 같다). */
    private Queue openReviews(long count, LocalDateTime now) {
        List<Dated> items = new ArrayList<>();
        if (count > 0) {
            int sla = groupBuyProperties.getOpenReview().getSlaBusinessDays();
            for (Object[] row : opsRepository.findOpenReviews()) {
                LocalDateTime submittedAt = (LocalDateTime) row[2];
                LocalDateTime base = submittedAt != null ? submittedAt : now;
                items.add(new Dated((Long) row[0], "오픈 승인 · " + row[1], pair((String) row[3], (String) row[4]),
                        dueAt(KstDates.toKstDate(base), sla), base, "GROUP_BUY"));
            }
        }
        return deadlined("OPEN_REVIEW", GROUP_BUYS, "오픈 승인", count, items, "/group-buys?queue=OPEN_REVIEW", now);
    }

    /** 입점 심사 — 신청 + 3영업일(가정값 · {@code admin.onboarding-sla-business-days}). */
    private List<Queue> onboarding(LocalDateTime now) {
        int sla = adminProperties.getOnboardingSlaBusinessDays();
        List<Dated> brand = new ArrayList<>();
        for (Object[] row : opsRepository.findPendingSellerApplications()) {
            LocalDateTime createdAt = (LocalDateTime) row[2];
            brand.add(new Dated((Long) row[0], "브랜드 입점 심사 · " + row[1], documentDetail(row[3], row[4], row[5]),
                    dueAt(KstDates.toKstDate(createdAt), sla), createdAt, "SELLER_APPLICATION"));
        }
        List<Dated> creator = new ArrayList<>();
        // 인플루언서 입점 심사의 건별 행 보조줄은 없다 — 시안에 인플루언서 D-1 행이 없다(56 E2).
        for (Object[] row : opsRepository.findPendingCreatorApplications()) {
            LocalDateTime createdAt = (LocalDateTime) row[2];
            creator.add(new Dated((Long) row[0], "인플루언서 입점 심사 · " + row[1], null, dueAt(KstDates.toKstDate(createdAt), sla),
                    createdAt, "CREATOR_APPLICATION"));
        }
        return List.of(
                deadlined("BRAND_APPLICATION", ONBOARDING, "브랜드 입점 심사", brand.size(), brand,
                        "/sellers/applications?status=PENDING", now),
                deadlined("CREATOR_APPLICATION", ONBOARDING, "인플루언서 입점 심사", creator.size(), creator,
                        "/creator/applications?status=PENDING", now));
    }

    /**
     * 세금계산서 — 발행(브랜드 · 발행 대기)은 마감 5영업일 전부터 임박 행으로 올린다(시안 ②). 대조(인플루언서 · 제출됨)는 대기 1행.
     * 두 큐의 합은 정산 요약의 「운영자 조치」({@code AdminEvidenceRows.operatorActionCount})와 같다.
     */
    private List<Queue> taxDocuments(LocalDateTime now) {
        List<SettlementTaxDocument> open = taxDocumentRepository.findOpen(TaxDocumentStatus.DONE);
        List<SettlementTaxDocument> toIssue = open.stream()
                .filter(d -> d.getType().isBrandInvoice() && d.getStatus() == TaxDocumentStatus.PENDING_ISSUE).toList();
        List<SettlementTaxDocument> toVerify = open.stream()
                .filter(d -> d.getType() == TaxDocumentType.CREATOR_TAX_INVOICE
                        && d.getStatus() == TaxDocumentStatus.SUBMITTED).toList();

        LocalDate today = KstDates.today(now);
        LocalDate dueSoonLimit = businessCalendar.addBusinessDays(today, adminProperties.getTaxInvoiceDueSoonBusinessDays());
        List<SettlementTaxDocument> urgent = toIssue.stream()
                .filter(d -> d.getDueDate() != null && !d.getDueDate().isAfter(dueSoonLimit))
                .sorted(Comparator.comparing(SettlementTaxDocument::getDueDate))
                .toList();
        // 대기 행 보조줄의 브랜드 수(55 계획서 A3)까지 한 번에 — 발행 대기 문서의 정산을 모두 읽는다.
        Map<Long, Settlement> settlements = toIssue.isEmpty() ? Map.of()
                : settlementRepository.findAllById(toIssue.stream().map(SettlementTaxDocument::getSettlementId).distinct()
                .toList()).stream().collect(Collectors.toMap(Settlement::getId, Function.identity(), (a, b) -> a));
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        for (SettlementTaxDocument document : urgent.stream().limit(ROW_LIMIT).toList()) {
            boolean overdue = document.getDueDate().isBefore(today);
            Settlement settlement = settlements.get(document.getSettlementId());
            rows.add(row(overdue ? TodoUrgency.OVERDUE : TodoUrgency.DUE_SOON, overdue ? "기한 초과" : "기한 임박",
                    "TAX_INVOICE_ISSUE",
                    "세금계산서 발행 · " + (settlement == null ? "정산 " + document.getSettlementId()
                            : settlement.getSettlementNumber()),
                    document.getType().getLabel(), SETTLEMENTS, ageText(document.getCreatedAt(), now),
                    "마감 " + document.getDueDate().format(MONTH_DAY),
                    new AdminHomeDto.Target("SETTLEMENT", document.getSettlementId(), null), 1));
        }
        long urgentRest = urgent.size() - rows.size();
        if (urgentRest > 0) {
            rows.add(row(TodoUrgency.DUE_SOON, "기한 임박", "TAX_INVOICE_ISSUE", "세금계산서 발행 기한 임박 외 " + urgentRest + "건",
                    null, SETTLEMENTS, null, null, new AdminHomeDto.Target("QUEUE", null, "/settlements?tab=EVIDENCE"),
                    urgentRest));
        }
        long rest = toIssue.size() - urgent.size();
        if (rest > 0) {
            List<SettlementTaxDocument> waitingDocuments = toIssue.stream().filter(d -> !urgent.contains(d)).toList();
            LocalDateTime oldest = waitingDocuments.stream()
                    .map(SettlementTaxDocument::getCreatedAt).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            rows.add(row(TodoUrgency.WAITING, "대기", "TAX_INVOICE_ISSUE", "세금계산서 발행",
                    issueDetail(waitingDocuments, settlements), SETTLEMENTS, ageText(oldest, now), null,
                    new AdminHomeDto.Target("QUEUE", null, "/settlements?tab=EVIDENCE"), rest));
        }
        LocalDateTime oldestSubmitted = toVerify.stream().map(SettlementTaxDocument::getSubmittedAt)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return List.of(new Queue("TAX_INVOICE_ISSUE", SETTLEMENTS, toIssue.size(), rows),
                waiting("TAX_INVOICE_VERIFY", SETTLEMENTS, "세금계산서 대조", "인플루언서 사업자 승인번호", toVerify.size(),
                        oldestSubmitted, "/settlements?tab=EVIDENCE", now));
    }

    /**
     * 발행 대기 행 보조줄(55 계획서 A3) — 「브랜드 3곳 · 홈택스 수동 · 11.10 마감」. 이 행이 대표하는 문서(기한 임박으로 올라간 건 제외)의
     * 브랜드 수와 가장 빠른 마감이다. 마감이 없으면 마감 문구를 뺀다.
     */
    private static String issueDetail(List<SettlementTaxDocument> documents, Map<Long, Settlement> settlements) {
        long brands = documents.stream().map(d -> settlements.get(d.getSettlementId())).filter(Objects::nonNull)
                .map(s -> s.getMarket().getId()).distinct().count();
        LocalDate due = documents.stream().map(SettlementTaxDocument::getDueDate).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        return join(join(brands == 0 ? null : "브랜드 " + brands + "곳", "홈택스 수동"),
                due == null ? null : due.format(MONTH_DAY) + " 마감");
    }

    /** 마감 있는 건 — 지났으면 기한 초과, 마감일이 다음 영업일 이내면 기한 임박(D-1 · 오늘 마감), 나머지는 대기 1행. */
    private Queue deadlined(String key, String menu, String label, long count, List<Dated> items, String filter,
                            LocalDateTime now) {
        LocalDate today = KstDates.today(now);
        LocalDate nextBusinessDay = businessCalendar.addBusinessDays(today, 1);
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        long itemized = 0;
        long overdueCount = 0;
        LocalDateTime oldestWaiting = null;
        for (Dated item : items) {
            boolean overdue = item.dueAt().isBefore(now);
            boolean dueSoon = !overdue && !KstDates.toKstDate(item.dueAt()).isAfter(nextBusinessDay);
            if (!overdue && !dueSoon) {
                oldestWaiting = oldestWaiting == null || item.enteredAt().isBefore(oldestWaiting) ? item.enteredAt() : oldestWaiting;
                continue;
            }
            itemized++;
            overdueCount += overdue ? 1 : 0;
            if (rows.size() >= ROW_LIMIT) {
                continue;
            }
            String badge = overdue ? "기한 초과" : KstDates.toKstDate(item.dueAt()).equals(today) ? "오늘 마감" : "D-1";
            rows.add(row(overdue ? TodoUrgency.OVERDUE : TodoUrgency.DUE_SOON, badge, key, item.title(), item.detail(), menu,
                    ageText(item.enteredAt(), now), "마감 " + item.dueAt().format(MONTH_DAY),
                    new AdminHomeDto.Target(item.targetType(), item.id(), null), 1));
        }
        long folded = itemized - rows.size();
        if (folded > 0) {
            rows.add(row(TodoUrgency.DUE_SOON, "기한 임박", key, label + " 기한 임박 외 " + folded + "건", null, menu, null, null,
                    new AdminHomeDto.Target("QUEUE", null, filter), folded));
        }
        long waiting = Math.max(0, count - itemized);
        if (waiting > 0) {
            rows.add(row(TodoUrgency.WAITING, "대기", key, label, aboveDetail(overdueCount, itemized - overdueCount), menu,
                    ageText(oldestWaiting, now), null, new AdminHomeDto.Target("QUEUE", null, filter), waiting));
        }
        return new Queue(key, menu, count, rows);
    }

    /**
     * 대기 행 보조줄(55 계획서 A3) — 같은 큐의 건별 행이 위에 올라가 있으면 「기한 초과 1건 · 기한 임박 1건은 위에」. 시안 「D-1 1건은 위
     * 기한 임박에」를 일반화했다 — 기한 임박에는 오늘 마감도 들어 「D-1」로 못 박지 않는다.
     */
    private static String aboveDetail(long overdue, long dueSoon) {
        List<String> parts = new ArrayList<>();
        if (overdue > 0) {
            parts.add("기한 초과 " + overdue + "건");
        }
        if (dueSoon > 0) {
            parts.add("기한 임박 " + dueSoon + "건");
        }
        return parts.isEmpty() ? null : String.join(" · ", parts) + "은 위에";
    }

    /** 오픈 승인 건별 행 보조줄(56 E2) — 「글로우랩 × 하린_뷰티랩」(오늘 일정의 공구 표기와 같다). 비어 있는 쪽은 뺀다. */
    private static String pair(String marketName, String showroomName) {
        String pair = java.util.stream.Stream.of(marketName, showroomName).filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" × "));
        return pair.isEmpty() ? null : pair;
    }

    /**
     * 브랜드 입점 심사 건별 행 보조줄(56 E2) — 「서류 3종」. 신청에 첨부된 서류(사업자등록증 · 통신판매업 신고증 · 통장 사본) 수이고, 하나도
     * 없으면 null.
     */
    private static String documentDetail(Object... urls) {
        long attached = java.util.Arrays.stream(urls).filter(u -> u instanceof String s && !s.isBlank()).count();
        return attached == 0 ? null : "서류 " + attached + "종";
    }

    private LocalDateTime dueAt(LocalDate base, int slaBusinessDays) {
        return KstDates.endOfDay(businessCalendar.addBusinessDays(base, slaBusinessDays));
    }

    // ------------------------------------------------------------------ 대기 큐 — 큐별 1행

    /**
     * 계약 조치 큐 5종(55 계획서 A1 · D19) — 사이드바 「계약 관리」 숫자는 계약 요약 {@code actionRequiredCount}(다섯 큐의 합)라 다섯을 다
     * 올린다(해지 요청만 올리면 사이드바와 어긋난다). 해지 요청 · 만료 확인에는 SLA 가 없어 기한 행 없이 대기 1행씩이다. 경과는 큐별 고정
     * 정렬의 기준 열(검토 요청 · 서명 확인 기준 · 서명 기한 · 재발송 요청 · 해지 요청).
     */
    private List<Queue> contracts(LocalDateTime now) {
        Map<AdminContractQueue, Long> counts = contractQueryService.summary().queues();
        List<Queue> queues = new ArrayList<>();
        for (AdminContractQueue queue : AdminContractQueue.values()) {
            long count = counts.getOrDefault(queue, 0L);
            queues.add(waiting("CONTRACT_" + queue.name(), CONTRACTS, contractLabel(queue), contractDetail(queue), count,
                    count == 0 ? null : contractQueryService.oldestInQueue(queue, now), "/contracts?queue=" + queue.name(),
                    now));
        }
        return queues;
    }

    /** 큐 이름 — 01-contracts rev.9 조치 큐(검토 대기 · 공구 생성 대기 · 만료 확인) · 1021 16절 「해지 요청」. */
    private static String contractLabel(AdminContractQueue queue) {
        return switch (queue) {
            case REVIEW -> "계약 검토 대기";
            case CONCLUSION -> "공구 생성 대기";
            case EXPIRY -> "계약 만료 확인";
            case RESEND -> "서명 안내 재발송 요청";
            case TERMINATION -> "계약 해지 요청";
        };
    }

    /** 보조줄 — 01-contracts rev.9 조치 큐 카드의 설명. 재발송 · 해지 요청은 시안에 카드가 없어 null. */
    private static String contractDetail(AdminContractQueue queue) {
        return switch (queue) {
            case REVIEW -> "브랜드 제출 · 승인 또는 반려";
            case CONCLUSION -> "양측 서명 완료 · PDF 업로드";
            case EXPIRY -> "서명 기한 경과 · 수동 종결";
            case RESEND, TERMINATION -> null;
        };
    }

    /** @param detail 보조줄(55 계획서 A3) — 시안에 「—」인 큐는 null. */
    private static Queue waiting(String key, String menu, String label, String detail, long count, LocalDateTime oldest,
                                 String filter, LocalDateTime now) {
        List<AdminHomeDto.TodoRow> rows = count == 0 ? List.of()
                : List.of(row(TodoUrgency.WAITING, "대기", key, label, detail, menu, ageText(oldest, now), null,
                new AdminHomeDto.Target("QUEUE", null, filter), count));
        return new Queue(key, menu, count, rows);
    }

    /** 건별 행이 상한에 걸리면 나머지를 같은 급한 정도의 「외 N건」 한 행으로 접는다. */
    private static Queue itemized(String key, String menu, long count, List<AdminHomeDto.TodoRow> rows, TodoUrgency urgency,
                                  String label, String filter) {
        List<AdminHomeDto.TodoRow> all = new ArrayList<>(rows);
        fold(all, count, urgency, key, label, menu, filter);
        return new Queue(key, menu, count, all);
    }

    private static void fold(List<AdminHomeDto.TodoRow> rows, long total, TodoUrgency urgency, String key, String label,
                             String menu, String filter) {
        long listed = rows.stream().filter(r -> r.urgency().equals(urgency.name())).mapToLong(AdminHomeDto.TodoRow::count).sum();
        long rest = total - listed;
        if (rest > 0) {
            rows.add(row(urgency, urgency.getLabel(), key, label + " 외 " + rest + "건", null, menu, null, null,
                    new AdminHomeDto.Target("QUEUE", null, filter), rest));
        }
    }

    private static AdminHomeDto.TodoRow row(TodoUrgency urgency, String badge, String queue, String title, String detail,
                                            String menu, String ageText, String dueText, AdminHomeDto.Target target,
                                            long count) {
        return new AdminHomeDto.TodoRow(urgency.name(), badge, queue, title, detail, menu, ageText, dueText, target, count);
    }

    static String ageText(LocalDateTime since, LocalDateTime now) {
        if (since == null) {
            return null;
        }
        Duration age = Duration.between(since, now);
        if (age.toMinutes() < 60) {
            return Math.max(0, age.toMinutes()) + "분 경과";
        }
        if (age.toHours() < 24) {
            return age.toHours() + "시간 경과";
        }
        return age.toDays() + "일 경과";
    }

    private static long tabCount(AdminTransactionDto.RefundSummary summary, String tab) {
        AdminTransactionDto.RefundTabStat stat = summary.tabs().get(tab);
        return stat == null ? 0 : stat.count();
    }

    private static String won(Integer amount) {
        return amount == null ? null : String.format("%,d원", amount);
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        return b == null ? a : a + " · " + b;
    }

    private static String snippet(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() > 40 ? flat.substring(0, 40) + "…" : flat;
    }

    /** @param detail 건별 행(기한 초과 · 기한 임박) 보조줄(56 E2) — 대기 1행에는 쓰지 않는다 */
    private record Dated(Long id, String title, String detail, LocalDateTime dueAt, LocalDateTime enteredAt,
                         String targetType) {
    }

    /** 큐 하나 — {@code rows} 의 건수 합 = {@code count}. */
    public record Queue(String key, String menu, long count, List<AdminHomeDto.TodoRow> rows) {
    }

    public record Snapshot(List<Queue> queues) {

        public long count(String key) {
            return queues.stream().filter(q -> q.key().equals(key)).mapToLong(Queue::count).sum();
        }

        public long menuTotal(String menu) {
            return queues.stream().filter(q -> q.menu().equals(menu)).mapToLong(Queue::count).sum();
        }

        public long total() {
            return queues.stream().mapToLong(Queue::count).sum();
        }

        public long rowCount(TodoUrgency urgency) {
            return rows().stream().filter(r -> r.urgency().equals(urgency.name())).mapToLong(AdminHomeDto.TodoRow::count).sum();
        }

        /** 급한 순 — 같은 급한 정도 안에서는 큐 순서(실패 · 기한 있는 큐 먼저)를 지킨다. */
        public List<AdminHomeDto.TodoRow> rows() {
            Map<String, Integer> rank = java.util.Arrays.stream(TodoUrgency.values())
                    .collect(Collectors.toMap(TodoUrgency::name, Enum::ordinal));
            return queues.stream().flatMap(q -> q.rows().stream())
                    .sorted(Comparator.comparing((AdminHomeDto.TodoRow r) -> rank.get(r.urgency())))
                    .toList();
        }

        public Map<String, Queue> byKey() {
            return queues.stream().collect(Collectors.toMap(Queue::key, Function.identity(), (a, b) -> a));
        }
    }
}
