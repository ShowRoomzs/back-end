package showroomz.api.admin.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
import showroomz.domain.member.brand.repository.BrandWithdrawalRequestRepository;
import showroomz.domain.metrics.repository.AdminOperationsQueryRepository;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.config.properties.AdminProperties;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.utils.BusinessCalendar;

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
        queues.add(waiting("CHANGE_REQUEST", ONBOARDING, "변경 요청 검토",
                changeRequestService.getSummary().getPendingCount(), opsRepository.oldestPendingChangeRequest(),
                "/change-requests?status=PENDING", now));
        queues.add(waiting("WITHDRAWAL", MEMBERS, "탈퇴 승인 검토",
                brandWithdrawalRepository.countPending() + opsRepository.countPendingCreatorWithdrawals(),
                opsRepository.oldestPendingWithdrawal(), "/members/withdrawals", now));
        queues.add(waiting("CLAWBACK_UNRECOVERABLE", SETTLEMENTS, "차감 미회수",
                clawbackRows.build(null).unrecoverableCount(), opsRepository.oldestUnrecoverableClawback(),
                "/settlements?tab=CLAWBACK", now));
        AdminOrderExceptionDto.ExceptionSummary exceptions = exceptionService.getSummary();
        queues.add(waiting("PROCESSING_DELAY", TRANSACTIONS, "처리 지연 확인",
                exceptions.tabCounts().getOrDefault("DELAY", 0L), null, "/order-exceptions?tab=DELAY", now));
        // 사이드바 배지 범위(order.exception.badge-scope)가 처리 지연만이면 배송 예외는 할 일에 넣지 않는다 — 합계가 배지와 같아야 한다.
        queues.add(waiting("DELIVERY_EXCEPTION", TRANSACTIONS, "배송 예외 확인",
                "ALL".equals(exceptions.badgeScope()) ? exceptions.tabCounts().getOrDefault("DELIVERY", 0L) : 0L,
                null, "/order-exceptions?tab=DELIVERY", now));
        queues.add(waiting("CLAIM_DISPUTE", TRANSACTIONS, "반려 이의 확인", claimRepository.countOpenDisputes(null),
                opsRepository.oldestOpenDispute(), "/claims?dispute=OPEN", now));
        queues.add(waiting("POST_REPORT", POSTS, "신고 게시물 검토", postQueryService.getSummary().getActionRequired(),
                null, "/posts?tab=REPORTED", now));
        queues.add(waiting("PRODUCT_INQUIRY_DELETE", CS, "상품 문의 삭제 요청",
                productInquiryService.getSummary().getDeleteRequestedCount(), null,
                "/product-inquiries?status=DELETE_REQUESTED", now));
        queues.addAll(contracts(now));
        queues.add(waiting("SUSPEND_REQUEST", GROUP_BUYS, "중단 요청",
                groupBuyQueues.getOrDefault(AdminGroupBuyQueue.SUSPEND_REQUEST, 0L),
                opsRepository.oldestPendingGroupBuyChangeRequest(ChangeRequestType.SUSPEND),
                "/group-buys?queue=SUSPEND_REQUEST", now));
        queues.add(waiting("EARLY_CLOSE_REQUEST", GROUP_BUYS, "조기 마감 요청",
                groupBuyQueues.getOrDefault(AdminGroupBuyQueue.EARLY_CLOSE_REQUEST, 0L),
                opsRepository.oldestPendingGroupBuyChangeRequest(ChangeRequestType.EARLY_CLOSE),
                "/group-buys?queue=EARLY_CLOSE_REQUEST", now));
        queues.add(waiting("APPEAL_REVIEW", GROUP_BUYS, "소명 검토",
                groupBuyQueues.getOrDefault(AdminGroupBuyQueue.APPEAL_REVIEW, 0L), null,
                "/group-buys?queue=APPEAL_REVIEW", now));
        return new Snapshot(queues);
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
            for (Object[] row : opsRepository.findPayoutFailedSettlements(ROW_LIMIT)) {
                Settlement settlement = (Settlement) row[0];
                rows.add(row(TodoUrgency.FAILED, "실패", "PAYOUT_FAILED", "분배 실패 · " + settlement.getSettlementNumber(),
                        settlement.getContract().getTitle(), SETTLEMENTS, ageText((LocalDateTime) row[1], now), null,
                        new AdminHomeDto.Target("SETTLEMENT", settlement.getId(), null), 1));
            }
        }
        return itemized("PAYOUT_FAILED", SETTLEMENTS, count, rows, TodoUrgency.FAILED, "분배 실패",
                "/settlements?tab=PAYOUT_FAILED");
    }

    private Queue operatorRefunds(long count, LocalDateTime now) {
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        if (count > 0) {
            for (OrderRefundTask task : opsRepository.findOperatorPendingRefunds(
                    showroomz.domain.order.type.RefundMethod.PG_CANCEL, ROW_LIMIT)) {
                rows.add(row(TodoUrgency.EXECUTION_PENDING, "집행 대기", "REFUND_EXECUTION",
                        "사유 환불 집행 대기 · " + task.getOrder().getOrderNumber(), won(task.getRefundAmount()),
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
                rows.add(row(TodoUrgency.OVERDUE, "기한 초과", "INQUIRY",
                        "1:1 문의 · " + (inquiry.getType() == null ? "기타" : inquiry.getType().getDescription()),
                        snippet(inquiry.getContent()), CS, ageText(inquiry.getCreatedAt(), now), "+" + overHours + "시간",
                        new AdminHomeDto.Target("INQUIRY", inquiry.getId(), null), 1));
            }
            fold(rows, overdue, TodoUrgency.OVERDUE, "INQUIRY", "1:1 문의 기한 초과", CS, "/inquiries?status=WAITING");
        }
        long inSla = Math.max(0, count - overdue);
        if (inSla > 0) {
            rows.add(row(TodoUrgency.WAITING, "대기", "INQUIRY", "1:1 문의(기한 내)", null, CS,
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
                items.add(new Dated((Long) row[0], "오픈 승인 · " + row[1], dueAt(base.toLocalDate(), sla), base,
                        "GROUP_BUY"));
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
            brand.add(new Dated((Long) row[0], "브랜드 입점 심사 · " + row[1], dueAt(createdAt.toLocalDate(), sla), createdAt,
                    "SELLER_APPLICATION"));
        }
        List<Dated> creator = new ArrayList<>();
        for (Object[] row : opsRepository.findPendingCreatorApplications()) {
            LocalDateTime createdAt = (LocalDateTime) row[2];
            creator.add(new Dated((Long) row[0], "인플루언서 입점 심사 · " + row[1], dueAt(createdAt.toLocalDate(), sla),
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

        LocalDate today = now.toLocalDate();
        LocalDate dueSoonLimit = businessCalendar.addBusinessDays(today, adminProperties.getTaxInvoiceDueSoonBusinessDays());
        List<SettlementTaxDocument> urgent = toIssue.stream()
                .filter(d -> d.getDueDate() != null && !d.getDueDate().isAfter(dueSoonLimit))
                .sorted(Comparator.comparing(SettlementTaxDocument::getDueDate))
                .toList();
        Map<Long, String> numbers = urgent.isEmpty() ? Map.of()
                : settlementRepository.findAllById(urgent.stream().map(SettlementTaxDocument::getSettlementId).toList())
                .stream().collect(Collectors.toMap(Settlement::getId, Settlement::getSettlementNumber, (a, b) -> a));
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        for (SettlementTaxDocument document : urgent.stream().limit(ROW_LIMIT).toList()) {
            boolean overdue = document.getDueDate().isBefore(today);
            rows.add(row(overdue ? TodoUrgency.OVERDUE : TodoUrgency.DUE_SOON, overdue ? "기한 초과" : "기한 임박",
                    "TAX_INVOICE_ISSUE",
                    "세금계산서 발행 · " + numbers.getOrDefault(document.getSettlementId(), "정산 " + document.getSettlementId()),
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
            LocalDateTime oldest = toIssue.stream().filter(d -> !urgent.contains(d))
                    .map(SettlementTaxDocument::getCreatedAt).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            rows.add(row(TodoUrgency.WAITING, "대기", "TAX_INVOICE_ISSUE", "세금계산서 발행", null, SETTLEMENTS,
                    ageText(oldest, now), null, new AdminHomeDto.Target("QUEUE", null, "/settlements?tab=EVIDENCE"), rest));
        }
        LocalDateTime oldestSubmitted = toVerify.stream().map(SettlementTaxDocument::getSubmittedAt)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return List.of(new Queue("TAX_INVOICE_ISSUE", SETTLEMENTS, toIssue.size(), rows),
                waiting("TAX_INVOICE_VERIFY", SETTLEMENTS, "세금계산서 대조", toVerify.size(), oldestSubmitted,
                        "/settlements?tab=EVIDENCE", now));
    }

    /** 마감 있는 건 — 지났으면 기한 초과, 마감일이 다음 영업일 이내면 기한 임박(D-1 · 오늘 마감), 나머지는 대기 1행. */
    private Queue deadlined(String key, String menu, String label, long count, List<Dated> items, String filter,
                            LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate nextBusinessDay = businessCalendar.addBusinessDays(today, 1);
        List<AdminHomeDto.TodoRow> rows = new ArrayList<>();
        long itemized = 0;
        LocalDateTime oldestWaiting = null;
        for (Dated item : items) {
            boolean overdue = item.dueAt().isBefore(now);
            boolean dueSoon = !overdue && !item.dueAt().toLocalDate().isAfter(nextBusinessDay);
            if (!overdue && !dueSoon) {
                oldestWaiting = oldestWaiting == null || item.enteredAt().isBefore(oldestWaiting) ? item.enteredAt() : oldestWaiting;
                continue;
            }
            itemized++;
            if (rows.size() >= ROW_LIMIT) {
                continue;
            }
            String badge = overdue ? "기한 초과" : item.dueAt().toLocalDate().equals(today) ? "오늘 마감" : "D-1";
            rows.add(row(overdue ? TodoUrgency.OVERDUE : TodoUrgency.DUE_SOON, badge, key, item.title(), null, menu,
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
            rows.add(row(TodoUrgency.WAITING, "대기", key, label, null, menu, ageText(oldestWaiting, now), null,
                    new AdminHomeDto.Target("QUEUE", null, filter), waiting));
        }
        return new Queue(key, menu, count, rows);
    }

    private LocalDateTime dueAt(LocalDate base, int slaBusinessDays) {
        return businessCalendar.addBusinessDays(base, slaBusinessDays).atTime(23, 59, 59);
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
            queues.add(waiting("CONTRACT_" + queue.name(), CONTRACTS, contractLabel(queue), count,
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

    private static Queue waiting(String key, String menu, String label, long count, LocalDateTime oldest, String filter,
                                 LocalDateTime now) {
        List<AdminHomeDto.TodoRow> rows = count == 0 ? List.of()
                : List.of(row(TodoUrgency.WAITING, "대기", key, label, null, menu, ageText(oldest, now), null,
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

    private record Dated(Long id, String title, LocalDateTime dueAt, LocalDateTime enteredAt, String targetType) {
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
