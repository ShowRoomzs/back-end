package showroomz.api.common.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 성과 지표 값 하나(48 파트너 설계서 0-5) — 값 · 상태 · 원래 값 · 근거 문장. 어느 값이 어느 상태인지는 서버가 정한다.
 *
 * <ul>
 *     <li>{@code value = null} — 「—」(아직 없음 · 측정 불가 · 정산 후 확정 · 협의 중). 0 은 측정된 0 이다.</li>
 *     <li>{@code original} — 조정 협의 · 합의로 바뀐 값의 원래 값(「협의 중 · 원래 4,712,800원」 · 「합의 반영 · 원래 578%」).</li>
 *     <li>{@code basis} — 산식 보조줄(「확정 거래액 ÷ 확정 주문 291건」). 관찰 사실 · 분모만 적는다(§47-0).</li>
 * </ul>
 */
@Schema(name = "PerformanceFigure", description = "성과 지표 값 — value 가 null 이면 「—」. 비율은 % 값(소수 1자리), ROAS 는 % 정수")
public record PerformanceFigure<T>(
        @Schema(nullable = true) T value,
        @Schema(description = "FINAL(확정) · PROJECTED(예정) · PENDING_SETTLEMENT(정산 후 확정) · NEGOTIATING(협의 중) · NOT_AVAILABLE(집계 준비 중)")
        FigureState state,
        @Schema(description = "원래 값 — 조정 협의 · 합의 반영일 때만", nullable = true) T original,
        @Schema(description = "산식 · 분모 보조줄", nullable = true) String basis
) {

    public static <T> PerformanceFigure<T> of(T value, FigureState state, String basis) {
        return new PerformanceFigure<>(value, state, null, basis);
    }

    public static <T> PerformanceFigure<T> fin(T value, String basis) {
        return new PerformanceFigure<>(value, FigureState.FINAL, null, basis);
    }

    public static <T> PerformanceFigure<T> none(FigureState state, String basis) {
        return new PerformanceFigure<>(null, state, null, basis);
    }
}
