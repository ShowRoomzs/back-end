package showroomz.api.common.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.api.app.claim.service.ClaimPaymentService;
import showroomz.api.app.order.service.PaymentConfirmService;
import showroomz.domain.order.entity.OrderClaimPayment;
import showroomz.domain.payment.entity.PaymentWebhookEvent;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.repository.PaymentWebhookEventRepository;
import showroomz.domain.payment.type.WebhookEventResult;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * 포트원 웹훅 처리(결제 계획서 5-7) — 서명 검증은 컨트롤러가 끝냈다. 여기서는 ② 이벤트 행(T11, REQUIRES_NEW) ③ 결제 조회
 * ④ confirm ⑤ 결과 기록(T12)을 한다. 응답 코드는 실패의 종류로 정한다 — 일시 장애·버그는 500(포트원이 재전송한다),
 * 모르는 결제·관심 없는 타입은 200.
 */
@Slf4j
@Service
public class PaymentWebhookService {

    private static final Set<String> CONFIRM_TYPES = Set.of("Transaction.Paid", "Transaction.Failed", "Transaction.Cancelled");

    private final PaymentWebhookEventRepository eventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentConfirmService confirmService;
    private final ClaimPaymentService claimPaymentService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTransaction;

    public PaymentWebhookService(PaymentWebhookEventRepository eventRepository, PaymentRepository paymentRepository,
                                 PaymentConfirmService confirmService, ClaimPaymentService claimPaymentService,
                                 ObjectMapper objectMapper,
                                 org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.paymentRepository = paymentRepository;
        this.confirmService = confirmService;
        this.claimPaymentService = claimPaymentService;
        this.objectMapper = objectMapper;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public enum Outcome {
        PROCESSED(200), DUPLICATE(200), IGNORED(200), FAILED(500);

        private final int httpStatus;

        Outcome(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    public Outcome handle(String webhookId, byte[] body, LocalDateTime now) {
        String payload = new String(body, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (IOException e) {
            log.warn("웹훅 본문 파싱 실패 - webhookId: {}", webhookId);
            return Outcome.IGNORED;
        }
        String type = root.path("type").asText(null);
        String paymentId = root.path("data").path("paymentId").asText(null);

        // T11 — 이벤트 행. 이미 처리된 id 면 아무것도 안 한다(진짜 중복). RECEIVED·FAILED 면 다시 처리한다.
        Long eventId;
        try {
            eventId = newTransaction.execute(tx -> upsertEvent(webhookId, paymentId, type, payload, now));
        } catch (DataIntegrityViolationException e) {
            // 같은 id 두 개가 동시에 들어와 둘째의 INSERT 가 유니크에 걸렸다 — 첫째가 처리한다.
            return Outcome.DUPLICATE;
        }
        if (eventId == null) {
            return Outcome.DUPLICATE;
        }

        // 클레임 재발송 배송비 결제는 paymentId 접두로 갈린다 — 수신 컨트롤러를 따로 두지 않는다(앱 클레임 설계서 3-3).
        if (OrderClaimPayment.isClaimPaymentId(paymentId)) {
            return handleClaimPayment(eventId, webhookId, paymentId, type, now);
        }
        if (paymentId == null || !paymentRepository.existsById(paymentId)) {
            log.info("모르는 결제의 웹훅 - webhookId: {}, paymentId: {}, type: {}", webhookId, paymentId, type);
            finish(eventId, WebhookEventResult.IGNORED, null, now);
            return Outcome.IGNORED;
        }
        if (type == null || !CONFIRM_TYPES.contains(type)) {
            finish(eventId, WebhookEventResult.IGNORED, null, now);
            return Outcome.IGNORED;
        }

        try {
            confirmService.confirm(paymentId, PaymentConfirmService.Trigger.WEBHOOK);
            finish(eventId, WebhookEventResult.PROCESSED, null, now);
            return Outcome.PROCESSED;
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_NOT_FOUND) {
                finish(eventId, WebhookEventResult.IGNORED, e.getMessage(), now);
                return Outcome.IGNORED;
            }
            // PAYMENT_GATEWAY_ERROR 등 일시 장애 — 500 으로 재전송을 받는다.
            log.warn("웹훅 처리 일시 실패 - webhookId: {}, paymentId: {} - {}", webhookId, paymentId, e.getMessage());
            finish(eventId, WebhookEventResult.FAILED, e.getMessage(), now);
            return Outcome.FAILED;
        } catch (RuntimeException e) {
            // 우리 코드의 버그 — Sentry 에 두 번 찍히는 게 「조용히 잃는 것」보다 낫다.
            log.error("웹훅 처리 실패 - webhookId: {}, paymentId: {}", webhookId, paymentId, e);
            finish(eventId, WebhookEventResult.FAILED, e.toString(), now);
            return Outcome.FAILED;
        }
    }

    /** 클레임 결제의 확정 — 주문 결제와 같은 결과 규칙(모르는 결제·무관한 이벤트는 무시, 일시 장애는 500 으로 재전송). */
    private Outcome handleClaimPayment(Long eventId, String webhookId, String paymentId, String type,
                                       LocalDateTime now) {
        if (type == null || !CONFIRM_TYPES.contains(type)) {
            finish(eventId, WebhookEventResult.IGNORED, null, now);
            return Outcome.IGNORED;
        }
        try {
            claimPaymentService.confirm(paymentId, true);
            finish(eventId, WebhookEventResult.PROCESSED, null, now);
            return Outcome.PROCESSED;
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_NOT_FOUND) {
                finish(eventId, WebhookEventResult.IGNORED, e.getMessage(), now);
                return Outcome.IGNORED;
            }
            log.warn("클레임 결제 웹훅 일시 실패 - webhookId: {}, paymentId: {} - {}", webhookId, paymentId,
                    e.getMessage());
            finish(eventId, WebhookEventResult.FAILED, e.getMessage(), now);
            return Outcome.FAILED;
        } catch (RuntimeException e) {
            log.error("클레임 결제 웹훅 처리 실패 - webhookId: {}, paymentId: {}", webhookId, paymentId, e);
            finish(eventId, WebhookEventResult.FAILED, e.toString(), now);
            return Outcome.FAILED;
        }
    }

    /** @return 처리할 이벤트 id — 이미 PROCESSED·IGNORED 면 null */
    private Long upsertEvent(String webhookId, String paymentId, String type, String payload, LocalDateTime now) {
        Optional<PaymentWebhookEvent> existing = eventRepository.findByWebhookId(webhookId);
        if (existing.isPresent()) {
            PaymentWebhookEvent event = existing.get();
            if (event.isDone()) {
                return null;
            }
            event.retry(now);
            return event.getId();
        }
        return eventRepository.saveAndFlush(PaymentWebhookEvent.received(webhookId, paymentId, type, payload, now)).getId();
    }

    /** T12 — 결과 기록. 처리 트랜잭션과 분리해 confirm 이 실패해도 기록은 남는다. */
    private void finish(Long eventId, WebhookEventResult result, String error, LocalDateTime now) {
        newTransaction.executeWithoutResult(tx -> eventRepository.findById(eventId)
                .ifPresent(event -> event.finish(result, error, now)));
    }
}
