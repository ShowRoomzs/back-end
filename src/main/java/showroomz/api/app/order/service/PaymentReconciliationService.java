package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.entity.PaymentReconciliationIssue;
import showroomz.domain.payment.repository.PaymentReconciliationIssueRepository;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.domain.payment.type.ReconciliationIssueKind;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOnePaymentGateway;
import showroomz.global.payment.portone.PortOneStatus;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일일 대사(결제 계획서 4-8) — 4-1~4-7 의 장치는 전부 「요청이 우리 서버에 도달했다」를 전제한다. 마지막 안전망은 정기 대사다.
 *
 * <p>자동 수정은 {@code confirm}을 다시 부르는 것뿐이다. 대사 배치가 상태를 직접 쓰기 시작하면 두 소스가 서로를 고치는 루프가 생긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private static final List<PortOneStatus> REMOTE_STATUSES =
            List.of(PortOneStatus.PAID, PortOneStatus.CANCELLED, PortOneStatus.PARTIAL_CANCELLED);
    private static final Set<PaymentStatus> OURS_PAID_LIKE = Set.of(PaymentStatus.PAID, PaymentStatus.CANCEL_REQUESTED);

    private final PortOnePaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final PaymentReconciliationIssueRepository issueRepository;
    private final PaymentConfirmService confirmService;
    private final PaymentAlerts alerts;
    private final TransactionTemplate transactionTemplate;

    /** 구간 대사 — 결과 요약을 돌려준다. 목록 조회 실패는 이번 날을 건너뛰고 경고만 남긴다. */
    public Result reconcile(LocalDateTime from, LocalDateTime until, LocalDateTime now) {
        List<PortOnePayment> remoteList;
        try {
            remoteList = gateway.listPayments(from, until, REMOTE_STATUSES);
        } catch (PaymentGatewayException | PaymentGatewayRejectedException e) {
            alerts.warning("일일 대사 - 포트원 목록 조회 실패(" + from + " ~ " + until + ") - " + e.getMessage());
            return Result.skippedResult();
        }
        Map<String, PortOnePayment> remote = remoteList.stream()
                .collect(Collectors.toMap(PortOnePayment::paymentId, Function.identity(), (a, b) -> b));
        Map<String, Payment> ours = loadOurs(from.minusDays(1), until.plusDays(1));

        Map<ReconciliationIssueKind, Integer> counts = new EnumMap<>(ReconciliationIssueKind.class);
        for (PortOnePayment portone : remoteList) {
            Payment mine = ours.get(portone.paymentId());
            if (portone.status() == PortOneStatus.PAID) {
                if (mine == null || !OURS_PAID_LIKE.contains(mine.getStatus())) {
                    record(ReconciliationIssueKind.PORTONE_PAID_NOT_OURS, portone, mine, now, counts);
                    if (mine != null) {
                        tryConfirm(portone.paymentId(), ReconciliationIssueKind.PORTONE_PAID_NOT_OURS, now);
                    }
                } else if (portone.totalAmount() != null && portone.totalAmount() != mine.getAmount().longValue()) {
                    record(ReconciliationIssueKind.AMOUNT_DIFF, portone, mine, now, counts);
                }
            } else if (portone.status() == PortOneStatus.CANCELLED) {
                if (mine != null && mine.getStatus() == PaymentStatus.PAID) {
                    record(ReconciliationIssueKind.PORTONE_CANCELLED_NOT_OURS, portone, mine, now, counts);
                    tryConfirm(portone.paymentId(), ReconciliationIssueKind.PORTONE_CANCELLED_NOT_OURS, now);
                }
            }
        }
        for (Payment mine : ours.values()) {
            if (mine.getStatus() != PaymentStatus.PAID) {
                continue;
            }
            if (mine.getCreatedAt() != null && (mine.getCreatedAt().isBefore(from) || !mine.getCreatedAt().isBefore(until))) {
                continue; // 여유 구간의 우리 결제는 포트원 목록에 없는 게 정상이다
            }
            PortOnePayment portone = remote.get(mine.getPaymentId());
            // 부분 환불된 결제는 포트원에서 PARTIAL_CANCELLED 로 보인다 — 누적 취소액이 같으면 정상이다(1009 기획 수정본 2-3).
            if (portone != null && portone.status() == PortOneStatus.PARTIAL_CANCELLED && mine.getCancelledAmount() > 0
                    && (portone.cancelledAmount() == null || portone.cancelledAmount() == mine.getCancelledAmount())) {
                continue;
            }
            if (portone == null || portone.status() != PortOneStatus.PAID) {
                record(ReconciliationIssueKind.OURS_PAID_NOT_PORTONE, portone, mine, now, counts);
            }
        }

        int issues = counts.values().stream().mapToInt(Integer::intValue).sum();
        String summary = "일일 대사 " + from.toLocalDate() + " - 포트원 " + remoteList.size() + "건 · 우리 " + ours.size()
                + "건 · 어긋남 " + issues + "건 " + counts;
        if (issues > 0) {
            alerts.warning(summary);
        } else {
            log.info(summary);
        }
        return new Result(false, remoteList.size(), ours.size(), counts);
    }

    private void tryConfirm(String paymentId, ReconciliationIssueKind kind, LocalDateTime now) {
        try {
            confirmService.confirm(paymentId, PaymentConfirmService.Trigger.RECONCILIATION);
            resolveIfConverged(paymentId, kind, now);
        } catch (BusinessException e) {
            log.warn("대사 confirm 실패 - paymentId: {} - {}", paymentId, e.getMessage());
        }
    }

    /** 같은 빈 안의 호출이라 @Transactional 프록시가 걸리지 않는다 — 트랜잭션 템플릿으로 경계를 잡는다. */
    private Map<String, Payment> loadOurs(LocalDateTime from, LocalDateTime until) {
        return transactionTemplate.execute(tx -> paymentRepository.findByCreatedAtBetween(from, until).stream()
                .collect(Collectors.toMap(Payment::getPaymentId, Function.identity(), (a, b) -> a)));
    }

    /** T16 — 어긋남 UPSERT. 같은 paymentId·kind 는 갱신한다(매일 새로 쌓지 않는다). */
    private void record(ReconciliationIssueKind kind, PortOnePayment portone, Payment mine, LocalDateTime now,
                        Map<ReconciliationIssueKind, Integer> counts) {
        String paymentId = portone != null ? portone.paymentId() : mine.getPaymentId();
        String portoneStatus = portone != null ? portone.status().name() : null;
        Long portoneAmount = portone != null ? portone.totalAmount() : null;
        String ourStatus = mine != null ? mine.getStatus().name() : null;
        Integer ourAmount = mine != null ? mine.getAmount() : null;
        transactionTemplate.executeWithoutResult(tx -> issueRepository.findByPaymentIdAndKind(paymentId, kind).ifPresentOrElse(
                issue -> issue.observedAgain(portoneStatus, portoneAmount, ourStatus, ourAmount, now),
                () -> issueRepository.save(PaymentReconciliationIssue.detected(paymentId, kind, portoneStatus, portoneAmount,
                        ourStatus, ourAmount, now))));
        counts.merge(kind, 1, Integer::sum);
    }

    /** confirm 이 닫았으면 이슈도 닫는다 — 대사가 발견한 건은 대부분 「confirm 이 한 번도 안 불린」 건이다. */
    private void resolveIfConverged(String paymentId, ReconciliationIssueKind kind, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(tx -> {
            Payment payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment == null) {
                return;
            }
            boolean converged = switch (kind) {
                case PORTONE_PAID_NOT_OURS -> payment.getStatus() == PaymentStatus.PAID
                        || payment.getStatus() == PaymentStatus.CANCELLED_MISMATCH
                        || payment.getStatus() == PaymentStatus.CANCEL_REQUESTED;
                case PORTONE_CANCELLED_NOT_OURS -> payment.getStatus() == PaymentStatus.CANCELLED
                        || payment.getStatus() == PaymentStatus.CANCELLED_MISMATCH;
                default -> false;
            };
            if (converged) {
                issueRepository.findByPaymentIdAndKind(paymentId, kind)
                        .ifPresent(issue -> issue.resolve("confirm 재호출로 수렴 - " + payment.getStatus(), now));
            }
        });
    }

    public record Result(boolean skipped, int remoteCount, int ourCount, Map<ReconciliationIssueKind, Integer> issues) {
        static Result skippedResult() {
            return new Result(true, 0, 0, Map.of());
        }
    }
}
