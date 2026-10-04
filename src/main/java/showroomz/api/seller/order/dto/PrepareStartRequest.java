package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 준비 시작(E8) — 다건도 같은 모달(건수만 교체). 효과는 소비자 단순 취소권 종료 하나다(§34-4). */
public record PrepareStartRequest(
        @Schema(description = "하위주문 id 목록 — 1~500건 · 중복 id는 한 번만 처리", example = "[1024, 1025, 1031]")
        @NotEmpty @Size(max = 500) List<Long> deliveryGroupIds
) {
}
