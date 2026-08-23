package showroomz.domain.notification.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 발송 한 묶음의 결과.
 *
 * <p>{@code invalidTokens}를 결과에 담아 <b>올려보내는</b> 이유 — 어느 토큰이 죽었는지는 발송
 * 어댑터만 알지만, 지우는 것은 도메인의 일이다. 어댑터가 직접 리포지토리를 지우면 채널 구현이
 * 저장소에 손을 대게 되고, 테스트에서 발송만 갈아끼울 수 없게 된다.
 */
public record PushResult(int successCount, int failureCount, List<String> invalidTokens) {

    public PushResult {
        invalidTokens = invalidTokens == null ? List.of() : List.copyOf(invalidTokens);
    }

    public static PushResult none() {
        return new PushResult(0, 0, List.of());
    }

    public PushResult plus(PushResult other) {
        List<String> merged = new ArrayList<>(this.invalidTokens);
        merged.addAll(other.invalidTokens);
        return new PushResult(
                this.successCount + other.successCount,
                this.failureCount + other.failureCount,
                merged);
    }

    /** 한 명이라도 실제로 받았는가 — 이력의 {@code delivered} 판단 기준이다 */
    public boolean anyDelivered() {
        return successCount > 0;
    }
}
