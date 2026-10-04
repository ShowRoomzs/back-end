package showroomz.domain.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.payment.entity.PaymentWebhookEvent;
import showroomz.domain.payment.type.WebhookEventResult;

import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByWebhookId(String webhookId);

    long countByResultAndAttemptsGreaterThanEqual(WebhookEventResult result, int attempts);
}
