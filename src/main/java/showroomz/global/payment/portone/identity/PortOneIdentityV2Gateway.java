package showroomz.global.payment.portone.identity;

import io.portone.sdk.server.errors.IdentityVerificationNotFoundException;
import io.portone.sdk.server.errors.PortOneException;
import io.portone.sdk.server.errors.UnknownException;
import io.portone.sdk.server.identityverification.FailedIdentityVerification;
import io.portone.sdk.server.identityverification.IdentityVerification;
import io.portone.sdk.server.identityverification.IdentityVerificationClient;
import io.portone.sdk.server.identityverification.IdentityVerificationFailure;
import io.portone.sdk.server.identityverification.IdentityVerificationVerifiedCustomer;
import io.portone.sdk.server.identityverification.ReadyIdentityVerification;
import io.portone.sdk.server.identityverification.VerifiedIdentityVerification;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOneProperties;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 포트원 V2 본인인증 — 서버 SDK {@link IdentityVerificationClient} 구현(57 설계서 2-2). 결제 {@code PortOneV2Gateway} 와 같은 자격
 * ({@code storeId · apiSecret · baseUrl}) · 같은 타임아웃 규칙을 쓰고 채널 키만 {@code portone.channel-keys.identity} 다.
 *
 * <p>실패의 종류 — 포트원이 오류 응답을 준 경우({@link PortOneException} · 4xx)는 {@link PaymentGatewayRejectedException}(설정 오류 ·
 * 재시도 무의미), 타임아웃 · 통신 · 5xx · 해석 불가({@link UnknownException})는 {@link PaymentGatewayException}(결과 모름 · 앱이
 * 다시 부른다). 포트원이 모르는 id(SDK 창을 열기 전)는 {@code Optional.empty()}.
 *
 * <p>CI · DI · 전화 · 이름은 로그 · 예외 메시지에 싣지 않는다 — 디버그 로그도 id · status · pgTxId 까지다(6-2).
 */
@Slf4j
@Component
@ConditionalOnExpression("${portone.enabled:true} and ${portone.identity.enabled:true}")
public class PortOneIdentityV2Gateway implements PortOneIdentityGateway {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PortOneProperties properties;
    private final IdentityVerificationClient client;

    public PortOneIdentityV2Gateway(PortOneProperties properties) {
        requireText(properties.getStoreId(), "portone.store-id");
        requireText(properties.getApiSecret(), "portone.api-secret");
        requireText(properties.getChannelKeys().getIdentity(), "portone.channel-keys.identity");
        this.properties = properties;
        this.client = new IdentityVerificationClient(properties.getApiSecret(), properties.getBaseUrl(), properties.getStoreId());
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 설정이 비어 있습니다. portone.identity.enabled=false 로 끄거나 값을 채우세요.");
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
    public String channelKey() {
        return properties.getChannelKeys().getIdentity();
    }

    @Override
    public Optional<IdentityVerificationResult> find(String identityVerificationId) {
        try {
            return Optional.of(toModel(await(client.getIdentityVerification(identityVerificationId))));
        } catch (PaymentGatewayRejectedException e) {
            if (e.getCause() instanceof IdentityVerificationNotFoundException) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** SDK 응답 → 우리 모델. 패키지 공개 — 단위 테스트가 SDK 객체를 만들어 직접 넣는다(실호출 없음). */
    static IdentityVerificationResult toModel(IdentityVerification verification) {
        if (verification instanceof VerifiedIdentityVerification verified) {
            IdentityVerificationVerifiedCustomer c = verified.getVerifiedCustomer();
            IdentityVerificationResult.VerifiedCustomer customer = new IdentityVerificationResult.VerifiedCustomer(
                    c.getName(),
                    c.getBirthDate(),
                    c.getGender() == null ? null : c.getGender().getValue(),
                    digits(c.getPhoneNumber()),
                    c.getOperator() == null ? null : c.getOperator().getValue(),
                    c.isForeigner(),
                    c.getCi(),
                    c.getDi());
            return IdentityVerificationResult.verified(verified.getId(), customer, verified.getPgTxId(), toLocal(verified.getVerifiedAt()));
        }
        if (verification instanceof FailedIdentityVerification failed) {
            IdentityVerificationFailure f = failed.getFailure();
            IdentityVerificationResult.Failure failure = f == null ? new IdentityVerificationResult.Failure(null, null, null)
                    : new IdentityVerificationResult.Failure(f.getReason(), f.getPgCode(), f.getPgMessage());
            return IdentityVerificationResult.failed(failed.getId(), failure, failed.getPgTxId());
        }
        if (verification instanceof ReadyIdentityVerification ready) {
            return IdentityVerificationResult.ready(ready.getId());
        }
        // Unrecognized — 새 SDK 가 모르는 상태를 줬다. 결과를 모르는 것으로 다룬다.
        throw new PaymentGatewayException("포트원 본인인증 응답을 해석할 수 없습니다: " + verification.getClass().getSimpleName());
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(properties.getReadTimeoutMillis() + properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PaymentGatewayException("포트원 본인인증 조회 타임아웃", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("포트원 본인인증 조회 중단", e);
        } catch (ExecutionException e) {
            throw translate(e.getCause());
        }
    }

    private static RuntimeException translate(Throwable cause) {
        if (cause instanceof UnknownException unknown) {
            log.error("포트원 본인인증 조회 실패(알 수 없는 응답) - {}", unknown.getMessage());
            return new PaymentGatewayException("포트원 본인인증 조회 실패: " + unknown.getMessage(), unknown);
        }
        if (cause instanceof PortOneException rejected) {
            String type = rejected.getClass().getSimpleName().replaceAll("Exception$", "");
            if (rejected instanceof IdentityVerificationNotFoundException) {
                // SDK 창을 열기 전에 완료를 부른 경우 — 정상 경로라 경고로 올리지 않는다.
                log.debug("포트원 본인인증 조회 - 없음(404)");
            } else {
                log.warn("포트원 본인인증 조회 거절 - type: {} - {}", type, rejected.getMessage());
            }
            return new PaymentGatewayRejectedException(400, type, rejected.getMessage(), rejected);
        }
        return new PaymentGatewayException("포트원 본인인증 조회 실패", cause);
    }

    private static String digits(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, KST);
    }
}
