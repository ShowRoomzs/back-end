package showroomz.api.test.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class TestOrderDto {

    @Schema(description = "배송완료 처리 결과 (테스트)")
    public record DeliveredResponse(
            @Schema(description = "주문번호", example = "20261014-000004")
            String orderNumber,

            @Schema(description = "배송완료로 바뀐 하위주문번호", example = "[\"20261014-000004-01\"]")
            List<String> deliveredSubOrderNumbers,

            @Schema(description = "배송완료 시각 — 구매확정 타이머가 여기서 시작한다", example = "2026-10-17T14:30:00")
            LocalDateTime deliveredAt
    ) {
    }
}
