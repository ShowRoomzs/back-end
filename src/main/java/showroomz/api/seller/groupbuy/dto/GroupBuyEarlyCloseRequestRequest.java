package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.EarlyCloseReasonCode;

/** 조기 마감 요청(C3). ETC면 메모 필수. */
@Schema(description = "조기 마감 요청 — 운영자 승인 시 공구가 종료(EARLY_CLOSED)된다")
public record GroupBuyEarlyCloseRequestRequest(

        @Schema(description = "사유 코드 — STOCK_OUT(재고 소진) · TARGET_REACHED(판매 목표 달성) · ETC(기타 — memo 필수)",
                example = "STOCK_OUT")
        @NotNull
        EarlyCloseReasonCode reasonCode,

        @Schema(description = "운영자에게 전달할 메모 — ETC면 필수(공백만 입력하면 빈 값으로 본다) · 1,000자",
                example = "크림 잔여 재고가 40개 미만입니다.", nullable = true)
        @Size(max = 1000)
        String memo
) {
}
