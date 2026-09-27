package showroomz.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.payment.type.WebhookEventResult;

import java.time.LocalDateTime;

/**
 * 포트원 웹훅 수신 기록(결제 계획서 3-2 · 5-7). {@code webhook_id}(헤더) 유니크가 중복 <b>처리</b>를 거른다.
 * 모르는 결제도 기록한다({@code payment_id} nullable) — 다른 환경의 결제일 수 있어 로그만 남긴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_webhook_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_webhook_event_webhook_id", columnNames = {"webhook_id"}),
        indexes = @Index(name = "idx_payment_webhook_event_result", columnList = "result, attempts")
)
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "webhook_id", nullable = false, length = 100)
    private String webhookId;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private WebhookEventResult result;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    private PaymentWebhookEvent(String webhookId, String paymentId, String eventType, String payload, LocalDateTime receivedAt) {
        this.webhookId = webhookId;
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.payload = payload;
        this.receivedAt = receivedAt;
        this.result = WebhookEventResult.RECEIVED;
        this.attempts = 1;
    }

    public static PaymentWebhookEvent received(String webhookId, String paymentId, String eventType, String payload,
                                               LocalDateTime receivedAt) {
        return new PaymentWebhookEvent(webhookId, paymentId, eventType, payload, receivedAt);
    }

    /** 재수신 — 이전 시도가 중간에 죽었거나 일시 장애였다. 다시 처리하고 시도 횟수를 올린다(5-7 ②). */
    public void retry(LocalDateTime receivedAt) {
        this.attempts += 1;
        this.receivedAt = receivedAt;
        this.result = WebhookEventResult.RECEIVED;
        this.errorMessage = null;
        this.processedAt = null;
    }

    public boolean isDone() {
        return result == WebhookEventResult.PROCESSED || result == WebhookEventResult.IGNORED;
    }

    public void finish(WebhookEventResult result, String errorMessage, LocalDateTime processedAt) {
        this.result = result;
        this.errorMessage = errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 1000) : errorMessage;
        this.processedAt = processedAt;
    }
}
