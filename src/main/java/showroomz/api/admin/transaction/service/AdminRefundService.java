package showroomz.api.admin.transaction.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.api.admin.transaction.AdminRefundNumber;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.dto.AdminTransactionDto.RefundTab;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.AdminRefundSearchCondition;
import showroomz.domain.order.repository.AdminRefundSearchCondition.DateColumn;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepositoryCustom.Stat;
import showroomz.domain.order.service.RefundExecutor;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 어드민 환불 관리(06c · 39 설계서) — <b>환불은 자동이 기본이고 이 화면은 예외만</b> 다룬다. 운영자가 손대는 것은 운영자 사유 환불의
 * 집행(재확인 다이얼로그 1회 · 2인 승인 없음 · 일괄 없음)과 PG 자동 환불 실패의 재시도 둘뿐이고, 둘은 같은 호출이다. 완료 탭은
 * PG 자동까지 모두 보여 준다 — CS 1순위 「환불 언제 들어와요?」에 경로와 상관없이 같은 화면에서 답한다.
 */
@Service
public class AdminRefundService {

    /** 완료 탭 기본 기간(시안 1개월 · 39 설계서 7-1 B-8). */
    static final int DEFAULT_DONE_DAYS = 30;

    private final OrderRefundTaskRepository refundTaskRepository;
    private final RefundExecutor refundExecutor;
    private final AdminRefundAssembler assembler;
    private final OrderProperties orderProperties;
    private final TransactionTemplate readOnlyTransaction;

    public AdminRefundService(OrderRefundTaskRepository refundTaskRepository, RefundExecutor refundExecutor,
                              AdminRefundAssembler assembler, OrderProperties orderProperties,
                              PlatformTransactionManager transactionManager) {
        this.refundTaskRepository = refundTaskRepository;
        this.refundExecutor = refundExecutor;
        this.assembler = assembler;
        this.orderProperties = orderProperties;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionDto.RefundItem> getRefunds(RefundTab tab, AdminTransactionDto.RefundRoute route,
                                                                   String keyword, AdminTransactionDto.RefundSort sort,
                                                                   Integer days, PagingRequest paging) {
        int size = paging.getSize();
        if (size < 1 || size > orderProperties.getListPageSizeMax()) {
            throw new BusinessException(ErrorCode.ORDER_PAGE_SIZE_INVALID);
        }
        Pageable pageable = PageRequest.of(Math.max(paging.getPage() - 1, 0), size);
        RefundTab resolved = tab == null ? RefundTab.PENDING : tab;
        Page<OrderRefundTask> page = refundTaskRepository.searchForAdmin(
                condition(resolved, route, keyword, sort, days, LocalDateTime.now()), pageable);
        return PageResponse.of(new PageImpl<>(assembler.items(page.getContent()), pageable, page.getTotalElements()));
    }

    /** 탭 숫자 · 합계 · 소비자 대기 · 배지 — 폴링 대상이라 GROUP BY 한 번 + 실패 최고령 한 번. */
    @Transactional(readOnly = true)
    public AdminTransactionDto.RefundSummary getSummary(Integer days) {
        LocalDateTime now = LocalDateTime.now();
        int doneDays = doneDays(days);
        long[] pending = new long[2];
        long[] failed = new long[2];
        long[] done = new long[2];
        Map<String, Long> byOrigin = new LinkedHashMap<>();
        for (RefundTaskOrigin origin : RefundTaskOrigin.values()) {
            byOrigin.put(origin.name(), 0L);
        }
        for (Stat stat : refundTaskRepository.summarizeForAdmin(now.minusDays(doneDays))) {
            switch (stat.status()) {
                case PENDING, EXECUTING -> {
                    if (stat.origin() == RefundTaskOrigin.OPERATOR) {
                        add(pending, stat);
                    }
                }
                case FAILED -> add(failed, stat);
                case DONE -> {
                    add(done, stat);
                    byOrigin.merge(stat.origin().name(), stat.count(), Long::sum);
                }
                case VOID -> {
                    // 어느 탭에도 없다.
                }
            }
        }
        LocalDateTime oldest = refundTaskRepository.oldestFailedCreatedAt();
        Integer waitingDays = oldest == null ? null : (int) Math.max(0, Duration.between(oldest, now).toDays());
        Map<String, AdminTransactionDto.RefundTabStat> tabs = new LinkedHashMap<>();
        tabs.put(RefundTab.PENDING.name(), new AdminTransactionDto.RefundTabStat(pending[0], pending[1], null, null, null));
        tabs.put(RefundTab.FAILED.name(), new AdminTransactionDto.RefundTabStat(failed[0], failed[1], waitingDays, null, null));
        tabs.put(RefundTab.DONE.name(), new AdminTransactionDto.RefundTabStat(done[0], done[1], null, doneDays,
                byOrigin));
        return new AdminTransactionDto.RefundSummary(tabs, pending[0] + failed[0]);
    }

    /** 상세 — M1 집행 재확인 · M2 재시도 다이얼로그의 데이터. 소멸(VOID) 건도 돌려준다(주문 상세에서 링크로 올 수 있다). */
    @Transactional(readOnly = true)
    public AdminTransactionDto.RefundDetail getRefund(Long refundTaskId) {
        return assembler.detail(refundTaskRepository.findWithOrder(refundTaskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_TASK_NOT_FOUND)));
    }

    /**
     * 집행 · 재시도 — 운영자 사유 환불의 [집행](대기)과 실패 건의 [재시도]가 같은 길이다. 돈이 나가는 순간은 이 호출 하나다.
     * 트랜잭션 밖에서 PG 를 부른다(집행기가 짧은 트랜잭션을 나눠 쓴다).
     */
    public AdminTransactionDto.RefundExecuteResponse execute(Long adminId, Long refundTaskId) {
        OrderRefundTask task = refundTaskRepository.findById(refundTaskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_TASK_NOT_FOUND));
        if (!task.isExecutable() || task.getPaymentId() == null) {
            throw new BusinessException(ErrorCode.REFUND_TASK_NOT_EXECUTABLE);
        }
        RefundExecutor.Outcome outcome = refundExecutor.execute(refundTaskId, adminId);
        // 집행 뒤 상태는 새 읽기 트랜잭션에서 — 이 메서드는 트랜잭션 밖이라 연관(주문 · 하위주문)을 그 안에서 읽어야 한다.
        AdminTransactionDto.RefundItem after = readOnlyTransaction.execute(tx ->
                assembler.item(refundTaskRepository.findWithOrder(refundTaskId).orElseThrow()));
        return new AdminTransactionDto.RefundExecuteResponse(outcome.name(), after);
    }

    /** 편입 철회(41 보고 2번) — 집행 전 운영자 사유 환불만. 반려 이의 인용 건은 거부한다(편입 때 재발송비 청구가 이미 정리됐다). */
    public AdminTransactionDto.RefundItem voidRefund(Long adminId, Long refundTaskId,
                                                     AdminTransactionDto.RefundVoidRequest request) {
        OrderRefundTask task = refundTaskRepository.findById(refundTaskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_TASK_NOT_FOUND));
        if (!task.isVoidable()) {
            throw new BusinessException(ErrorCode.REFUND_TASK_NOT_VOIDABLE);
        }
        if (!refundExecutor.voidTask(refundTaskId, request.reason(), adminId, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFUND_TASK_NOT_VOIDABLE);
        }
        return readOnlyTransaction.execute(tx -> assembler.item(refundTaskRepository.findWithOrder(refundTaskId).orElseThrow()));
    }

    /** 수동 완료 기록(41 보고 2번) — PG 콘솔 등 밖에서 돌려준 환불. PG 를 부르지 않고 집행 완료와 같은 후속을 적는다. */
    public AdminTransactionDto.RefundItem recordManual(Long adminId, Long refundTaskId,
                                                       AdminTransactionDto.RefundManualCompleteRequest request) {
        OrderRefundTask task = refundTaskRepository.findById(refundTaskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_TASK_NOT_FOUND));
        if (!task.isExecutable()) {
            throw new BusinessException(ErrorCode.REFUND_TASK_NOT_EXECUTABLE);
        }
        if (!refundExecutor.recordManual(refundTaskId, request.pgCancellationId(), request.note(), adminId,
                LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFUND_TASK_NOT_EXECUTABLE);
        }
        return readOnlyTransaction.execute(tx -> assembler.item(refundTaskRepository.findWithOrder(refundTaskId).orElseThrow()));
    }

    /** 탭 → 상태 · 출처 · 기간 · 「일시」 열, 검색어 → 환불번호 · 주문번호 · PG 거래번호(39 설계서 0-5 · 1-2 · 3-1). */
    static AdminRefundSearchCondition condition(RefundTab tab, AdminTransactionDto.RefundRoute route, String keyword,
                                                AdminTransactionDto.RefundSort sort, Integer days, LocalDateTime now) {
        Set<RefundTaskStatus> statuses = switch (tab) {
            case PENDING -> EnumSet.of(RefundTaskStatus.PENDING, RefundTaskStatus.EXECUTING);
            case FAILED -> EnumSet.of(RefundTaskStatus.FAILED);
            case DONE -> EnumSet.of(RefundTaskStatus.DONE);
        };
        // 집행 대기는 운영자 사유만 — PG 자동 대기는 커밋 직후 집행돼 머물지 않는다(39 설계서 0-3).
        RefundTaskOrigin origin = tab == RefundTab.PENDING ? RefundTaskOrigin.OPERATOR : null;
        LocalDateTime executedFrom = tab == RefundTab.DONE ? now.minusDays(doneDays(days)) : null;
        DateColumn column = switch (tab) {
            case PENDING -> DateColumn.CREATED;
            case FAILED -> DateColumn.MODIFIED;
            case DONE -> DateColumn.EXECUTED;
        };
        String text = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Long refundTaskId = text == null ? null : AdminRefundNumber.parseOrNull(text);
        boolean prefixed = AdminRefundNumber.hasPrefix(text);
        return new AdminRefundSearchCondition(statuses, origin, executedFrom,
                route == null ? null : route.getSources(), refundTaskId, prefixed ? null : text,
                prefixed && refundTaskId == null, column, sort == AdminTransactionDto.RefundSort.AMOUNT_DESC);
    }

    /** 완료 탭 기간(일) — 목록과 요약이 같은 값을 써야 탭 숫자 = 목록 건수다. 기본 30 · 1 미만은 1. */
    static int doneDays(Integer days) {
        return days == null ? DEFAULT_DONE_DAYS : Math.max(1, days);
    }

    private static void add(long[] bucket, Stat stat) {
        bucket[0] += stat.count();
        bucket[1] += stat.amount();
    }
}
