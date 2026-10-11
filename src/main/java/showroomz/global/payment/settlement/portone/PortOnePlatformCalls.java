package showroomz.global.payment.settlement.portone;

import io.portone.sdk.server.errors.PortOneException;
import io.portone.sdk.server.errors.UnknownException;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SDK {@code CompletableFuture} 를 설정의 읽기 타임아웃으로 끊고 실패의 종류를 가른다({@code PortOneV2Gateway} 와 같은 방식 · 포트원
 * 설계서 0-8) — 포트원이 오류 응답을 준 것({@link PortOneException})은 {@link PaymentGatewayRejectedException}(결과 확정),
 * 타임아웃 · 통신 · 5xx · 해석 불가({@link UnknownException})는 {@link PaymentGatewayException}(결과 모름).
 */
final class PortOnePlatformCalls {

    private final long timeoutMillis;

    PortOnePlatformCalls(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    <T> T await(CompletableFuture<T> future, String action) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PaymentGatewayException("포트원 Platform " + action + " 타임아웃", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("포트원 Platform " + action + " 중단", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnknownException) {
                throw new PaymentGatewayException("포트원 Platform " + action + " 결과 모름 — " + cause.getMessage(), cause);
            }
            if (cause instanceof PortOneException rejected) {
                throw new PaymentGatewayRejectedException(400, rejected.getClass().getSimpleName(),
                        "포트원 Platform " + action + " 거절 — " + rejected.getMessage(), rejected);
            }
            throw new PaymentGatewayException("포트원 Platform " + action + " 실패", cause);
        }
    }
}
