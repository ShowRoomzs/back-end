package showroomz.api.common.payment.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.domain.settlement.service.SettlementPayoutWebhookService;
import showroomz.global.config.properties.TossPayoutsProperties;
import showroomz.global.payment.settlement.toss.TossPayoutsWebhookVerifier;

import java.time.LocalDateTime;

/**
 * 토스 지급대행 웹훅 수신(44 토스 설계서 5-4 · 6-2) — 역할 없는 서버 간 호출이라 {@code common}에 둔다. {@code SecurityConfig} 화이트리스트.
 *
 * <p>원문 바이트로 서명({@code HMAC-SHA256(보안 키, "{body}:{transmission-time}")})을 검증한다 — 실패면 401 이고 본문을 파싱하지 않는다.
 * 보안 키가 비어 있으면({@code SIMULATED} 로컬 · CI) 검증을 건너뛰지 않고 <b>모두 거절</b>한다(결제 웹훅 관례). 처리는 DB 전이만이라
 * 10초 안에 끝난다 — 토스는 200 이 아니면 최대 7회 재전송한다.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/v1/webhooks/toss/payouts")
public class TossPayoutWebhookController {

    static final String SIGNATURE_HEADER = "tosspayments-webhook-signature";
    static final String TRANSMISSION_TIME_HEADER = "tosspayments-webhook-transmission-time";
    static final String RETRIED_COUNT_HEADER = "tosspayments-webhook-transmission-retried-count";

    private final SettlementPayoutWebhookService webhookService;
    private final TossPayoutsWebhookVerifier verifier;

    public TossPayoutWebhookController(SettlementPayoutWebhookService webhookService, TossPayoutsProperties properties) {
        this.webhookService = webhookService;
        String key = properties.getSecurityKey();
        TossPayoutsWebhookVerifier built = null;
        if (key != null && !key.isBlank()) {
            try {
                built = new TossPayoutsWebhookVerifier(key);
            } catch (IllegalArgumentException e) {
                log.warn("토스 지급대행 보안 키 형식 오류 — 웹훅을 모두 거절한다: {}", e.getMessage());
            }
        }
        this.verifier = built;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
                                        @RequestHeader(value = TRANSMISSION_TIME_HEADER, required = false) String transmissionTime,
                                        @RequestHeader(value = RETRIED_COUNT_HEADER, required = false) String retriedCount,
                                        @RequestBody(required = false) byte[] body) {
        if (verifier == null || body == null || !verifier.verify(body, transmissionTime, signature)) {
            log.warn("토스 지급대행 웹훅 서명 검증 실패 - retried: {}", retriedCount);
            return ResponseEntity.status(401).build();
        }
        SettlementPayoutWebhookService.Outcome outcome = webhookService.handle(body, LocalDateTime.now());
        return ResponseEntity.status(outcome.httpStatus()).build();
    }
}
