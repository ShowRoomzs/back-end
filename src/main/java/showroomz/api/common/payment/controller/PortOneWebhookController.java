package showroomz.api.common.payment.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.common.payment.service.PaymentWebhookService;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.payment.portone.PortOneProperties;
import showroomz.global.payment.portone.PortOneWebhookVerifier;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 포트원 웹훅 수신(결제 계획서 5-7) — 역할 없는 서버 간 호출이라 {@code common}에 둔다. {@code SecurityConfig} 화이트리스트.
 *
 * <p>원문 바이트로 서명을 검증한다 — 실패면 401 이고 본문을 파싱하지 않는다. 발신 IP 허용 목록은 두지 않는다(6-2).
 * 시크릿이 비어 있으면({@code portone.enabled=false} 로컬·CI) 서명 검증을 건너뛰지 않고 <b>모두 거절</b>한다 — 운영에서
 * 시크릿이 빠진 채 열리는 것보다 낫다. 테스트는 {@code portone.webhook-secret}에 고정 시크릿을 넣는다.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/v1/webhooks/portone")
public class PortOneWebhookController {

    private final PaymentWebhookService webhookService;
    private final PortOneWebhookVerifier verifier;

    public PortOneWebhookController(PaymentWebhookService webhookService, PortOneProperties portOneProperties,
                                    OrderProperties orderProperties) {
        this.webhookService = webhookService;
        String secret = portOneProperties.getWebhookSecret();
        this.verifier = secret == null || secret.isBlank()
                ? null : new PortOneWebhookVerifier(secret, orderProperties.getWebhookToleranceSeconds());
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "webhook-id", required = false) String webhookId,
                                        @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
                                        @RequestHeader(value = "webhook-signature", required = false) String signature,
                                        @RequestBody(required = false) byte[] body) {
        if (verifier == null || body == null
                || !verifier.verify(body, webhookId, timestamp, signature, Instant.now())) {
            log.warn("포트원 웹훅 서명 검증 실패 - webhookId: {}", webhookId);
            return ResponseEntity.status(401).build();
        }
        PaymentWebhookService.Outcome outcome = webhookService.handle(webhookId, body, LocalDateTime.now());
        return ResponseEntity.status(outcome.httpStatus()).build();
    }
}
