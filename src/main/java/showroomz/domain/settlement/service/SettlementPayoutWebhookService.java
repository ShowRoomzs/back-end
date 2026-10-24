package showroomz.domain.settlement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.entity.SettlementPayoutWebhookEvent;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway.LineResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutLookup;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementPayoutWebhookEventRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 토스 지급대행 웹훅 처리(44 토스 설계서 4-3 · 5-4) — 서명 검증은 컨트롤러가 끝냈다. ① {@code eventId} 멱등 ② {@code payout.changed} 는
 * 우리 행을 찾아 폴링과 <b>같은 전이</b>({@code applyLookupResults})로 닫는다 ③ {@code seller.changed} 는 셀러 상태 갱신 · 보류 즉시 해제.
 * 외부 호출은 없다 — 10초 안에 200 을 준다. 일시 장애 · 버그는 500(토스가 재전송) · 모르는 지급 · 셀러는 200(IGNORED).
 */
@Slf4j
@Service
public class SettlementPayoutWebhookService {

    public static final String EVENT_PAYOUT_CHANGED = "payout.changed";
    public static final String EVENT_SELLER_CHANGED = "seller.changed";

    public enum Outcome {
        APPLIED(200), DUPLICATE(200), IGNORED(200), FAILED(500);

        private final int httpStatus;

        Outcome(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    private final SettlementPayoutWebhookEventRepository eventRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementPayoutGateway gateway;
    private final SettlementPayoutTransitions transitions;
    private final SettlementPartnerSyncService partnerSync;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTransaction;

    public SettlementPayoutWebhookService(SettlementPayoutWebhookEventRepository eventRepository,
                                          SettlementPayoutRepository payoutRepository, SettlementRepository settlementRepository,
                                          SettlementPayoutGateway gateway, SettlementPayoutTransitions transitions,
                                          SettlementPartnerSyncService partnerSync, ObjectMapper objectMapper,
                                          PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.payoutRepository = payoutRepository;
        this.settlementRepository = settlementRepository;
        this.gateway = gateway;
        this.transitions = transitions;
        this.partnerSync = partnerSync;
        this.objectMapper = objectMapper;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Outcome handle(byte[] body, LocalDateTime now) {
        String payload = new String(body, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (IOException e) {
            log.warn("토스 지급대행 웹훅 본문 파싱 실패");
            return Outcome.IGNORED;
        }
        String eventId = root.path("eventId").asText(null);
        String eventType = root.path("eventType").asText(null);
        JsonNode entity = root.path("entityBody");
        String entityId = entity.path("id").asText(null);
        if (eventId == null || eventType == null) {
            return Outcome.IGNORED;
        }
        // ① 멱등 — 이미 처리(APPLIED · IGNORED)한 이벤트는 다시 처리하지 않는다. FAILED 로 남은 재전송은 다시 처리한다.
        Boolean fresh = newTransaction.execute(tx -> {
            Optional<SettlementPayoutWebhookEvent> existing = eventRepository.findById(eventId);
            if (existing.isPresent()) {
                return !existing.get().isDone();
            }
            eventRepository.save(SettlementPayoutWebhookEvent.received(eventId, eventType, entityId, payload, now));
            return true;
        });
        if (!Boolean.TRUE.equals(fresh)) {
            return Outcome.DUPLICATE;
        }
        Outcome outcome;
        try {
            outcome = switch (eventType) {
                case EVENT_PAYOUT_CHANGED -> onPayoutChanged(entity, now);
                case EVENT_SELLER_CHANGED -> partnerSync.onPartnerStatusChanged(entityId, entity.path("status").asText(null), now)
                        ? Outcome.APPLIED : Outcome.IGNORED;
                default -> Outcome.IGNORED;
            };
        } catch (RuntimeException e) {
            log.error("토스 지급대행 웹훅 처리 실패 - eventId: {}, type: {}", eventId, eventType, e);
            outcome = Outcome.FAILED;
        }
        String result = outcome == Outcome.FAILED ? SettlementPayoutWebhookEvent.RESULT_FAILED
                : outcome == Outcome.APPLIED ? SettlementPayoutWebhookEvent.RESULT_APPLIED : SettlementPayoutWebhookEvent.RESULT_IGNORED;
        newTransaction.executeWithoutResult(tx -> eventRepository.findById(eventId).ifPresent(e -> e.finish(result)));
        return outcome;
    }

    /** 지급 id → 우리 행(없으면 참조 id 로 — 지시 중 통신 실패로 id 가 비어 있던 행). 이미 닫힌 행 · 우리 행이 아니면 IGNORED. */
    private Outcome onPayoutChanged(JsonNode entity, LocalDateTime now) {
        String payoutId = entity.path("id").asText(null);
        String refPayoutId = entity.path("refPayoutId").asText(null);
        SettlementPayout row = newTransaction.execute(tx -> {
            Optional<SettlementPayout> found = payoutId == null ? Optional.empty() : payoutRepository.findByPgPayoutId(payoutId);
            if (found.isEmpty() && refPayoutId != null) {
                found = payoutRepository.findByPgTransferId(refPayoutId);
            }
            return found.orElse(null);
        });
        if (row == null) {
            log.info("토스 지급대행 웹훅 — 우리 행 아님 - payoutId: {}, ref: {}", payoutId, refPayoutId);
            return Outcome.IGNORED;
        }
        if (row.getStatus() != PayoutStatus.REQUESTED) {
            return Outcome.IGNORED;
        }
        String number = newTransaction.execute(tx -> settlementRepository.findById(row.getSettlementId())
                .map(Settlement::getSettlementNumber).orElse(null));
        PayoutLookup lookup = new PayoutLookup(row.getId(), row.getSettlementId(), number, row.getPayee(), row.getAmount(),
                row.getAttempt(), row.getPgTransferId(), row.getPgPayoutId(), row.getRequestedAt());
        Optional<LineResult> result = gateway.fromWebhook(entity.toString(), lookup);
        if (result.isEmpty()) {
            return Outcome.IGNORED;
        }
        transitions.applyLookupResults(row.getSettlementId(), List.of(result.get()), now);
        return Outcome.APPLIED;
    }
}
