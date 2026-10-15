package showroomz.domain.settlement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.settlement.entity.SettlementPayoutWebhookEvent;

public interface SettlementPayoutWebhookEventRepository extends JpaRepository<SettlementPayoutWebhookEvent, String> {
}
