package showroomz.global.payment.portone;

import io.portone.sdk.server.common.Currency;
import io.portone.sdk.server.common.PageInput;
import io.portone.sdk.server.errors.PaymentAlreadyCancelledException;
import io.portone.sdk.server.errors.PaymentNotFoundException;
import io.portone.sdk.server.errors.PortOneException;
import io.portone.sdk.server.errors.UnknownException;
import io.portone.sdk.server.payment.CancelPaymentResponse;
import io.portone.sdk.server.payment.CancelRequester;
import io.portone.sdk.server.payment.CancelledPayment;
import io.portone.sdk.server.payment.FailedPayment;
import io.portone.sdk.server.payment.FailedPaymentCancellation;
import io.portone.sdk.server.payment.GetPaymentsResponse;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PartialCancelledPayment;
import io.portone.sdk.server.payment.PayPendingPayment;
import io.portone.sdk.server.payment.Payment;
import io.portone.sdk.server.payment.PaymentCancellation;
import io.portone.sdk.server.payment.PaymentClient;
import io.portone.sdk.server.payment.PaymentFailure;
import io.portone.sdk.server.payment.PaymentFilterInput;
import io.portone.sdk.server.payment.PaymentMethodSerializer;
import io.portone.sdk.server.payment.PaymentSerializer;
import io.portone.sdk.server.payment.PaymentStatus;
import io.portone.sdk.server.payment.PaymentTimestampType;
import io.portone.sdk.server.payment.ReadyPayment;
import io.portone.sdk.server.payment.SucceededPaymentCancellation;
import io.portone.sdk.server.payment.VirtualAccountIssuedPayment;
import jakarta.annotation.PreDestroy;
import kotlinx.serialization.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 포트원 V2 — 서버 SDK({@code io.portone:server-sdk}) 구현(결제 계획서 6-1). 서비스 계층은 {@link PortOnePaymentGateway}만 본다.
 *
 * <p>실패의 종류를 가른다 — 포트원이 오류 응답을 준 경우({@link PortOneException}, 4xx)는
 * {@link PaymentGatewayRejectedException}(결과 확정), 타임아웃·통신 오류·5xx·해석 불가 응답({@link UnknownException})은
 * {@link PaymentGatewayException}(결과 모름)이다. 취소에서 이 구분이 돈이다(2-3 ⑧).
 *
 * <p>SDK 의 내부 타임아웃은 60초라 그대로 두면 결제 확정 요청이 1분씩 매달린다 — 설정의 읽기 타임아웃으로 {@code Future}를 끊는다.
 * API Secret 은 로그·예외 메시지에 싣지 않는다(6-3).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "portone", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PortOneV2Gateway implements PortOnePaymentGateway {

    private static final int LIST_PAGE_SIZE = 100;
    private static final int LIST_MAX_PAGES = 200;

    private final PortOneProperties properties;
    private final PaymentClient client;

    public PortOneV2Gateway(PortOneProperties properties) {
        requireText(properties.getStoreId(), "portone.store-id");
        requireText(properties.getApiSecret(), "portone.api-secret");
        requireText(properties.getWebhookSecret(), "portone.webhook-secret");
        requireText(properties.getChannelKeys().getCard(), "portone.channel-keys.card");
        this.properties = properties;
        this.client = new PaymentClient(properties.getApiSecret(), properties.getBaseUrl(), properties.getStoreId());
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 설정이 비어 있습니다. portone.enabled=false 로 끄거나 값을 채우세요.");
        }
    }

    @PreDestroy
    public void close() {
        client.close();
    }

    @Override
    public String storeId() {
        return properties.getStoreId();
    }

    @Override
    public void preRegister(String paymentId, long totalAmount, String currency) {
        await(client.preRegisterPayment(paymentId, totalAmount, null, currencyOf(currency)), "사전 등록");
    }

    @Override
    public Optional<PortOnePayment> getPayment(String paymentId) {
        try {
            return Optional.of(toModel(await(client.getPayment(paymentId), "결제 조회")));
        } catch (PaymentGatewayRejectedException e) {
            if (e.getCause() instanceof PaymentNotFoundException) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public PortOneCancelResult cancel(String paymentId, long amount, String reason) {
        String cancelReason = reason == null || reason.isBlank() ? "주문 취소" : reason;
        CancelPaymentResponse response;
        try {
            // amount 를 비우면 전액 취소다. 현재 취소 가능 금액을 함께 보내 이미 부분 취소된 결제면 PG 가 거절하게 한다.
            response = await(client.cancelPayment(paymentId, null, null, null, cancelReason, CancelRequester.Customer.INSTANCE,
                    null, amount, null, null, null), "결제 취소");
        } catch (PaymentGatewayRejectedException e) {
            if (e.getCause() instanceof PaymentAlreadyCancelledException) {
                return new PortOneCancelResult(PortOneCancelResult.Outcome.ALREADY_CANCELLED, null, e.getMessage());
            }
            throw e;
        }
        PaymentCancellation cancellation = response.getCancellation();
        String raw = cancellation.toString();
        if (cancellation instanceof SucceededPaymentCancellation succeeded) {
            return new PortOneCancelResult(PortOneCancelResult.Outcome.SUCCEEDED, succeeded.getPgCancellationId(), raw);
        }
        if (cancellation instanceof FailedPaymentCancellation failed) {
            throw new PaymentGatewayRejectedException(200, "CANCELLATION_FAILED", failed.getReason());
        }
        return new PortOneCancelResult(PortOneCancelResult.Outcome.PENDING, null, raw);
    }

    @Override
    public List<PortOnePayment> listPayments(LocalDateTime from, LocalDateTime until, List<PortOneStatus> statuses) {
        List<PaymentStatus> sdkStatuses = statuses.stream().map(PortOneV2Gateway::statusOf).filter(java.util.Objects::nonNull).toList();
        PaymentFilterInput filter = new PaymentFilterInput(null, properties.getStoreId(), PaymentTimestampType.CreatedAt.INSTANCE,
                toInstant(from), toInstant(until), sdkStatuses,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        List<PortOnePayment> result = new ArrayList<>();
        for (int page = 0; page < LIST_MAX_PAGES; page++) {
            GetPaymentsResponse response = await(client.getPayments(new PageInput(page, LIST_PAGE_SIZE), filter), "결제 목록 조회");
            List<Payment> items = response.getItems();
            for (Payment item : items) {
                result.add(toModel(item));
            }
            if (items.size() < LIST_PAGE_SIZE) {
                break;
            }
        }
        return result;
    }

    @Override
    public Optional<String> channelKeyFor(PaymentMethod method, EasyPayProvider easyPayProvider) {
        PortOneProperties.ChannelKeys keys = properties.getChannelKeys();
        String key;
        if (method == PaymentMethod.CARD) {
            key = keys.getCard();
        } else if (easyPayProvider == null) {
            return Optional.empty();
        } else {
            key = switch (easyPayProvider) {
                case KAKAOPAY -> keys.getKakaopay();
                case NAVERPAY -> keys.getNaverpay();
                case TOSSPAY -> keys.getTosspay();
            };
            // 비워 두면 카드 채널을 따라간다(허브형 — 6-2). "-" 는 명시적으로 끈 것이다.
            if (key == null || key.isBlank()) {
                key = keys.getCard();
            }
        }
        if (key == null || key.isBlank() || PortOneProperties.DISABLED_CHANNEL.equals(key.trim())) {
            return Optional.empty();
        }
        return Optional.of(key.trim());
    }

    // ------------------------------------------------------------------ SDK 호출

    /** SDK 의 Future 를 설정 타임아웃으로 기다린다. 예외는 「거절」과 「모름」 둘로 접는다. */
    private <T> T await(CompletableFuture<T> future, String action) {
        try {
            return future.get(properties.getReadTimeoutMillis() + properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PaymentGatewayException("포트원 " + action + " 타임아웃", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("포트원 " + action + " 중단", e);
        } catch (ExecutionException e) {
            throw translate(e.getCause(), action);
        }
    }

    private static RuntimeException translate(Throwable cause, String action) {
        if (cause instanceof UnknownException unknown) {
            // 5xx · 해석 불가 응답 — 결과를 모른다.
            log.error("포트원 {} 실패(알 수 없는 응답) - {}", action, unknown.getMessage());
            return new PaymentGatewayException("포트원 " + action + " 실패: " + unknown.getMessage(), unknown);
        }
        if (cause instanceof PortOneException rejected) {
            String type = typeOf(rejected);
            if (rejected instanceof PaymentNotFoundException) {
                // 결제창을 연 적 없는 결제는 404 가 정상이다(만료 전 조회에서 흔하다) — 경고로 올리지 않는다.
                log.debug("포트원 {} - 결제 없음(404)", action);
            } else {
                log.warn("포트원 {} 거절 - type: {} - {}", action, type, rejected.getMessage());
            }
            return new PaymentGatewayRejectedException(400, type, rejected.getMessage(), rejected);
        }
        return new PaymentGatewayException("포트원 " + action + " 통신 실패: " + cause, cause);
    }

    /** {@code PaymentNotFoundException} → {@code PAYMENT_NOT_FOUND} — 포트원 오류 코드와 같은 꼴로 남긴다. */
    private static String typeOf(PortOneException e) {
        String name = e.getClass().getSimpleName().replaceAll("Exception$", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    // ------------------------------------------------------------------ 변환

    private static PortOnePayment toModel(Payment payment) {
        if (!(payment instanceof Payment.Recognized recognized)) {
            return new PortOnePayment(null, PortOneStatus.UNKNOWN, null, null, null, null, null, null, null, null, null, null,
                    String.valueOf(payment));
        }
        PortOneStatus status = statusOf(payment);
        LocalDateTime paidAt = null;
        LocalDateTime cancelledAt = null;
        String failCode = null;
        String failMessage = null;
        if (payment instanceof PaidPayment paid) {
            paidAt = toLocal(paid.getPaidAt());
        } else if (payment instanceof CancelledPayment cancelled) {
            paidAt = toLocal(cancelled.getPaidAt());
            cancelledAt = toLocal(cancelled.getCancelledAt());
        } else if (payment instanceof FailedPayment failed) {
            PaymentFailure failure = failed.getFailure();
            if (failure != null) {
                failCode = failure.getPgCode() != null ? failure.getPgCode() : failure.getReason();
                failMessage = failure.getPgMessage() != null ? failure.getPgMessage() : failure.getReason();
            }
        }
        return new PortOnePayment(
                recognized.getId(),
                status,
                recognized.getStoreId(),
                recognized.getCurrency() != null ? recognized.getCurrency().getValue() : null,
                recognized.getAmount() != null ? recognized.getAmount().getTotal() : null,
                recognized.getTransactionId(),
                recognized.getChannel() != null && recognized.getChannel().getPgProvider() != null
                        ? recognized.getChannel().getPgProvider().getValue() : null,
                methodJson(recognized),
                paidAt,
                cancelledAt,
                failCode,
                failMessage,
                rawJson(payment)
        );
    }

    private static PortOneStatus statusOf(Payment payment) {
        if (payment instanceof PaidPayment) return PortOneStatus.PAID;
        if (payment instanceof FailedPayment) return PortOneStatus.FAILED;
        if (payment instanceof CancelledPayment) return PortOneStatus.CANCELLED;
        if (payment instanceof PartialCancelledPayment) return PortOneStatus.PARTIAL_CANCELLED;
        if (payment instanceof ReadyPayment) return PortOneStatus.READY;
        if (payment instanceof PayPendingPayment) return PortOneStatus.PAY_PENDING;
        if (payment instanceof VirtualAccountIssuedPayment) return PortOneStatus.VIRTUAL_ACCOUNT_ISSUED;
        return PortOneStatus.UNKNOWN;
    }

    private static PaymentStatus statusOf(PortOneStatus status) {
        return switch (status) {
            case PAID -> PaymentStatus.Paid.INSTANCE;
            case FAILED -> PaymentStatus.Failed.INSTANCE;
            case CANCELLED -> PaymentStatus.Cancelled.INSTANCE;
            case PARTIAL_CANCELLED -> PaymentStatus.PartialCancelled.INSTANCE;
            case READY -> PaymentStatus.Ready.INSTANCE;
            case PENDING, PAY_PENDING -> PaymentStatus.Pending.INSTANCE;
            case VIRTUAL_ACCOUNT_ISSUED -> PaymentStatus.VirtualAccountIssued.INSTANCE;
            case UNKNOWN -> null;
        };
    }

    private static Currency currencyOf(String currency) {
        return currency == null || "KRW".equals(currency) ? Currency.Krw.INSTANCE : new Currency.Unrecognized(currency);
    }

    /** 원문 — 분쟁 근거({@code payment.raw_response}). 직렬화가 막히면 상태 확정을 막지 않고 toString 으로 남긴다. */
    private static String rawJson(Payment payment) {
        try {
            return Json.Default.encodeToString(PaymentSerializer.INSTANCE, payment);
        } catch (RuntimeException e) {
            return String.valueOf(payment);
        }
    }

    private static String methodJson(Payment.Recognized payment) {
        if (payment.getMethod() == null) {
            return null;
        }
        try {
            return Json.Default.encodeToString(PaymentMethodSerializer.INSTANCE, payment.getMethod());
        } catch (RuntimeException e) {
            return String.valueOf(payment.getMethod());
        }
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static Instant toInstant(LocalDateTime local) {
        return local.atZone(ZoneId.systemDefault()).toInstant();
    }
}
