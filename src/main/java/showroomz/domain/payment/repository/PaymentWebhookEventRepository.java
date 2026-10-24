package showroomz.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.payment.entity.PaymentWebhookEvent;
import showroomz.domain.payment.type.WebhookEventResult;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByWebhookId(String webhookId);

    long countByResultAndAttemptsGreaterThanEqual(WebhookEventResult result, int attempts);

    /** 웹훅 처리 실패 지표의 가장 이른 수신 시각(55 계획서 A4) — 건수와 같은 조건. */
    @Query("SELECT MIN(e.receivedAt) FROM PaymentWebhookEvent e WHERE e.result = :result AND e.attempts >= :attempts")
    LocalDateTime findOldestReceivedAt(@Param("result") WebhookEventResult result, @Param("attempts") int attempts);
}
