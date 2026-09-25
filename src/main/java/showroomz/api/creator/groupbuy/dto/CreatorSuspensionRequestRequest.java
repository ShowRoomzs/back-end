package showroomz.api.creator.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.groupbuy.type.CreatorSuspensionReason;

/**
 * 공구 중단 요청(C7). 메모는 <b>항상 필수</b>다 — 인플루언서 요청은 상대(브랜드)의 이행 문제를 주장하는 경우가 대부분이라
 * 운영자가 사실을 확인할 재료가 필요하다(31 설계 5-3). 메모는 운영자에게 쓰는 글이고 브랜드에게 내리지 않는다.
 */
@Schema(description = "공구 중단 요청")
public record CreatorSuspensionRequestRequest(

        @Schema(description = "PRODUCT_DEFECT · DELIVERY_FAILURE · CONSUMER_COMPLAINTS · BRAND_UNREACHABLE · "
                + "PERSONAL_REASON · ETC", example = "DELIVERY_FAILURE")
        @NotNull
        CreatorSuspensionReason reasonCode,

        @Schema(description = "운영자에게 전달할 내용 — 필수 · 1,000자", example = "종료 후 3일이 지났는데 18건이…")
        @NotBlank
        @Size(max = 1000)
        String memo
) {
}
