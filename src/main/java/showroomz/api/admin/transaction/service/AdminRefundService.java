package showroomz.api.admin.transaction.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.domain.order.entity.OrderRefundTask;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.service.RefundExecutor;
import showroomz.domain.order.type.RefundTaskOrigin;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 어드민 환불 관리(06c · 1009 기획 수정본 8-2) — <b>환불은 자동이 기본이고 이 화면은 예외만</b> 다룬다. 운영자가 손대는 것은 운영자
 * 사유 환불의 집행(재확인 다이얼로그 1회 · 2인 승인 없음 · 일괄 없음)과 PG 자동 환불 실패의 재시도 둘뿐이다. 완료 탭은 PG 자동까지
 * 모두 보여 준다 — CS 1순위 「환불 언제 들어와요?」에 경로와 상관없이 같은 화면에서 답한다.
 */
@Service
public class AdminRefundService {

    private final OrderRefundTaskRepository refundTaskRepository;
    private final RefundExecutor refundExecutor;
    private final OrderProperties orderProperties;
    private final TransactionTemplate readOnlyTransaction;

    public AdminRefundService(OrderRefundTaskRepository refundTaskRepository, RefundExecutor refundExecutor,
                              OrderProperties orderProperties, PlatformTransactionManager transactionManager) {
        this.refundTaskRepository = refundTaskRepository;
        this.refundExecutor = refundExecutor;
        this.orderProperties = orderProperties;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionDto.RefundItem> getRefunds(AdminTransactionDto.RefundTab tab, Integer days,
                                                                   PagingRequest paging) {
        int size = paging.getSize();
        if (size < 1 || size > orderProperties.getListPageSizeMax()) {
            throw new BusinessException(ErrorCode.ORDER_PAGE_SIZE_INVALID);
        }
        Pageable pageable = PageRequest.of(Math.max(paging.getPage() - 1, 0), size);
        AdminTransactionDto.RefundTab resolved = tab == null ? AdminTransactionDto.RefundTab.PENDING : tab;
        Set<RefundTaskStatus> statuses = switch (resolved) {
            case PENDING -> EnumSet.of(RefundTaskStatus.PENDING, RefundTaskStatus.EXECUTING);
            case FAILED -> EnumSet.of(RefundTaskStatus.FAILED);
            case DONE -> EnumSet.of(RefundTaskStatus.DONE);
        };
        // 집행 대기는 운영자 사유만(PG 자동 대기는 커밋 직후 집행되므로 머물지 않는다) · 완료 탭 기본 조회 기간 1개월.
        RefundTaskOrigin origin = resolved == AdminTransactionDto.RefundTab.PENDING ? RefundTaskOrigin.OPERATOR : null;
        LocalDateTime from = resolved == AdminTransactionDto.RefundTab.DONE
                ? LocalDateTime.now().minusDays(days == null ? 30 : Math.max(1, days)) : LocalDateTime.of(2000, 1, 1, 0, 0);
        Page<OrderRefundTask> page = refundTaskRepository.findForAdmin(statuses, origin, from, pageable);
        return PageResponse.of(new PageImpl<>(page.getContent().stream().map(AdminRefundService::toItem).toList(),
                pageable, page.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public AdminTransactionDto.RefundSummary getSummary() {
        long pending = 0;
        long failed = 0;
        long done = 0;
        for (Object[] row : refundTaskRepository.countByStatusAndOrigin()) {
            RefundTaskStatus status = (RefundTaskStatus) row[0];
            RefundTaskOrigin origin = (RefundTaskOrigin) row[1];
            long count = (Long) row[2];
            if ((status == RefundTaskStatus.PENDING || status == RefundTaskStatus.EXECUTING)
                    && origin == RefundTaskOrigin.OPERATOR) {
                pending += count;
            } else if (status == RefundTaskStatus.FAILED) {
                failed += count;
            } else if (status == RefundTaskStatus.DONE) {
                done += count;
            }
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(AdminTransactionDto.RefundTab.PENDING.name(), pending);
        counts.put(AdminTransactionDto.RefundTab.FAILED.name(), failed);
        counts.put(AdminTransactionDto.RefundTab.DONE.name(), done);
        return new AdminTransactionDto.RefundSummary(counts, pending + failed);
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
                toItem(refundTaskRepository.findById(refundTaskId).orElseThrow()));
        return new AdminTransactionDto.RefundExecuteResponse(outcome.name(), after);
    }

    static AdminTransactionDto.RefundItem toItem(OrderRefundTask task) {
        return new AdminTransactionDto.RefundItem(
                task.getId(), task.getOrder().getId(), task.getOrder().getOrderNumber(), task.getDeliveryGroup().getId(),
                task.getDeliveryGroup().getSubOrderNumber(), task.getDeliveryGroup().getMarketName(), task.getSource(),
                sourceLabel(task), task.getOrigin(), task.getOrigin().getLabel(),
                task.getReasonCode() == null ? null : task.getReasonCode().getLabel(), task.getReasonDetail(),
                task.getRefundAmount(), task.getStatus(), statusLabel(task.getStatus()), task.getAttempt(),
                task.getLastError(), task.getRequestedBy(), task.getCreatedAt(), task.getExecutedAt(),
                task.getExecutedBy(), task.isExecutable() && task.getPaymentId() != null);
    }

    private static String sourceLabel(OrderRefundTask task) {
        return switch (task.getSource()) {
            case CANCEL_REQUEST_APPROVED -> "취소 요청 승인";
            case SELLER_DIRECT_CANCEL -> "판매 취소";
            case RETURN_COMPLETED -> "반송 완료";
            case CLAIM_RETURN_PASSED -> "반품 검수 통과";
            case OPERATOR_REASON -> "운영자 사유 환불";
        };
    }

    private static String statusLabel(RefundTaskStatus status) {
        return switch (status) {
            case PENDING -> "집행 대기";
            case EXECUTING -> "환불 처리 중";
            case DONE -> "환불 완료";
            case FAILED -> "환불 실패";
            case VOID -> "취소됨";
        };
    }
}
