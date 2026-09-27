package showroomz.api.seller.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.SuspensionReasonCode;

/** 공구 중단 요청(C2 · C4). 재고 소진은 중단이 아니라 조기 마감이다. ETC면 메모 필수. */
@Schema(description = "공구 중단 요청 — 운영자 승인 시 공구가 중단(SUSPENDED · 재개 불가)된다. 승인 전까지 판매는 계속된다")
public record GroupBuySuspensionRequestRequest(

        @Schema(description = "사유 코드 — QUALITY_ISSUE(상품 품질 이슈) · PRICE_TERMS_ERROR(가격·조건 오기) · "
                + "NEGOTIATION_BROKEN(인플루언서와 협의 결렬) · ETC(기타 — memo 필수). 재고 소진은 조기 마감 사유다",
                example = "QUALITY_ISSUE")
        @NotNull
        SuspensionReasonCode reasonCode,

        @Schema(description = "운영자에게 전달할 메모 — ETC면 필수(공백만 입력하면 빈 값으로 본다) · 1,000자",
                example = "입고분에서 용기 불량이 확인되었습니다.", nullable = true)
        @Size(max = 1000)
        String memo
) {
}
