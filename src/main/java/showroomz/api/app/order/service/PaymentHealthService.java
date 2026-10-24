package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.payment.repository.PaymentReconciliationIssueRepository;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.repository.PaymentWebhookEventRepository;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.domain.payment.type.WebhookEventResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 운영 지표 5종(결제 계획서 4-8). 다섯이 0이면 결제 시스템이 스스로 수렴하고 있다는 뜻이고, 하나라도 0이 아니면 그 줄이
 * 운영자가 오늘 볼 곳이다. 같은 쿼리를 운영자가 직접 돌릴 수 있게 {@code dev/결제 운영 지표.sql}에도 남겼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentHealthService {

    public static final int CANCEL_STUCK_MINUTES = 30;
    public static final int MISMATCH_WINDOW_HOURS = 24;
    public static final int MISMATCH_THRESHOLD = 3;
    public static final int WEBHOOK_FAILED_ATTEMPTS = 3;

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentReconciliationIssueRepository issueRepository;
    private final PaymentAlerts alerts;

    @Transactional(readOnly = true)
    public List<Metric> measure(LocalDateTime now) {
        List<Metric> metrics = new ArrayList<>();
        LocalDateTime stuckBefore = now.minusMinutes(CANCEL_STUCK_MINUTES);
        metrics.add(metric("취소 미수렴(CANCEL_REQUESTED 30분 초과)",
                paymentRepository.countByStatusAndCancelRequestedAtBefore(PaymentStatus.CANCEL_REQUESTED, stuckBefore), 1,
                () -> paymentRepository.findOldestCancelRequestedAtBefore(PaymentStatus.CANCEL_REQUESTED, stuckBefore)));
        metrics.add(metric("운영자 처리 대기(CANCEL_FAILED)",
                paymentRepository.countByStatus(PaymentStatus.CANCEL_FAILED), 1,
                () -> paymentRepository.findOldestCancelFailedSince(PaymentStatus.CANCEL_FAILED)));
        LocalDateTime mismatchAfter = now.minusHours(MISMATCH_WINDOW_HOURS);
        metrics.add(metric("자동 취소 발생(최근 24시간)",
                paymentRepository.countByStatusAndModifiedAtAfter(PaymentStatus.CANCELLED_MISMATCH, mismatchAfter),
                MISMATCH_THRESHOLD,
                () -> paymentRepository.findOldestModifiedAtAfter(PaymentStatus.CANCELLED_MISMATCH, mismatchAfter)));
        metrics.add(metric("웹훅 처리 실패(3회 이상)",
                webhookEventRepository.countByResultAndAttemptsGreaterThanEqual(WebhookEventResult.FAILED,
                        WEBHOOK_FAILED_ATTEMPTS), 1,
                () -> webhookEventRepository.findOldestReceivedAt(WebhookEventResult.FAILED, WEBHOOK_FAILED_ATTEMPTS)));
        metrics.add(metric("대사 미해결", issueRepository.countByResolvedAtIsNull(), 1,
                issueRepository::findOldestUnresolvedDetectedAt));
        return metrics;
    }

    /** 가장 이른 시각은 임계를 넘은 지표만 읽는다 — 정상일 때 쿼리를 늘리지 않는다(55 계획서 A4). */
    private static Metric metric(String name, long value, long threshold, Supplier<LocalDateTime> oldestAt) {
        return new Metric(name, value, threshold, value >= threshold ? oldestAt.get() : null);
    }

    /** 임계값을 넘는 지표마다 경고 1회. */
    public List<Metric> check(LocalDateTime now) {
        List<Metric> metrics = measure(now);
        List<Metric> exceeded = metrics.stream().filter(Metric::exceeded).toList();
        for (Metric metric : exceeded) {
            alerts.warning("결제 운영 지표 - " + metric.name() + ": " + metric.value() + "건 (임계값 " + metric.threshold() + ")");
        }
        if (exceeded.isEmpty()) {
            log.debug("결제 운영 지표 정상 - {}", metrics);
        }
        return exceeded;
    }

    /**
     * @param oldestAt 그 지표에 걸린 건 중 가장 이른 기준 시각 — 임계를 넘은 지표만 채운다(아니면 null). 어드민 홈 PG 장애 시작 시각
     *                 (55 계획서 A4)이 이 값들의 최솟값이다. 데이터에서 구하므로 재기동 · 다중 인스턴스에도 같다.
     */
    public record Metric(String name, long value, long threshold, LocalDateTime oldestAt) {
        public boolean exceeded() {
            return value >= threshold;
        }
    }
}
