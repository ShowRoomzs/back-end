package showroomz.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 토스 지급대행 웹훅 수신 기록(44 토스 설계서 3-3 · 5-4). {@code eventId} 가 PK 라 같은 이벤트를 두 번 <b>처리</b>하지 않는다(토스는 10초 내
 * 200 이 아니면 최대 7회 재전송). 우리 행이 아닌 지급 · 셀러도 기록한다({@code IGNORED}).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "settlement_payout_webhook_event")
public class SettlementPayoutWebhookEvent {

    public static final String RESULT_APPLIED = "APPLIED";
    public static final String RESULT_IGNORED = "IGNORED";
    public static final String RESULT_FAILED = "FAILED";
    private static final int BODY_MAX = 60_000;

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "result", nullable = false, length = 16)
    private String result;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    public static SettlementPayoutWebhookEvent received(String eventId, String eventType, String entityId, String body,
                                                        LocalDateTime receivedAt) {
        SettlementPayoutWebhookEvent event = new SettlementPayoutWebhookEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.entityId = entityId;
        event.body = body != null && body.length() > BODY_MAX ? body.substring(0, BODY_MAX) : body;
        event.receivedAt = receivedAt;
        event.result = RESULT_FAILED;   // 처리 전 — 끝나면 바꾼다. 중간에 죽으면 FAILED 로 남고 재전송이 다시 처리한다.
        return event;
    }

    public void finish(String result) {
        this.result = result;
    }

    public boolean isDone() {
        return RESULT_APPLIED.equals(result) || RESULT_IGNORED.equals(result);
    }
}
