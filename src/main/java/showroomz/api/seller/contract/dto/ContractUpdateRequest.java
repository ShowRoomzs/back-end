package showroomz.api.seller.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 임시저장(B1·B2) — <b>전체 교체(PUT 시맨틱)</b>다.
 *
 * <p>계약 폼은 항목 배열을 포함한 하나의 덩어리라 PATCH로 쪼개면 "3번째 행 삭제"를 표현할 수 없다.
 * 항목은 요청 배열로 통째 교체하고 {@code sort_order}는 배열 index로 채운다(설계서 4-2).
 *
 * <p>여기서 보는 것은 <b>형식뿐</b>이다 — 길이·숫자 범위·enum·10원 단위. 「필수」 판정은
 * 검토 요청 시점에만 한다(설계서 0-3). 그러지 않으면 시안이 보장한
 * "검토 요청 전까지 임시저장할 수 있습니다"가 거짓이 된다.
 */
@Schema(description = "계약 임시저장 요청 — 전체 교체")
public record ContractUpdateRequest(

        @Schema(description = "상세 조회로 받은 낙관적 락 버전. 다른 탭에서 먼저 저장됐으면 409(설계서 3-4)",
                example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long version,

        @Schema(description = "계약 상대 — 스레드 경유로 고정된 계약이면 다른 값을 보낼 수 없다", nullable = true)
        Long creatorId,

        @Schema(description = "공구명 — 2~40자 판정은 검토 요청 시점에 한다", nullable = true)
        @Size(max = 40)
        String title,

        @Schema(nullable = true) LocalDateTime groupBuyStartAt,
        @Schema(nullable = true) LocalDateTime groupBuyEndAt,

        @Schema(description = "고정 지급비(원) · 0원 허용 · 최대 1,000만원", nullable = true)
        @Min(0) @Max(10_000_000)
        Integer fixedFeeAmount,

        @Schema(nullable = true) FixedFeeTrigger fixedFeeTrigger,

        @Schema(description = "고지 확인 체크 — true로 바뀌는 순간의 시각이 저장된다. "
                + "false를 보내면 확인 시각이 지워진다", nullable = true)
        Boolean fixedFeeNoticeAgreed,

        @Schema(nullable = true) @Min(0) Integer contentFeedCount,
        @Schema(nullable = true) @Min(0) Integer contentReelsCount,
        @Schema(nullable = true) @Min(0) Integer contentStoryCount,

        @Schema(description = "게시 완료 기한", nullable = true) LocalDate contentDueDate,

        @Schema(nullable = true) Boolean secondaryUseAllowed,
        @Schema(nullable = true) SecondaryUsePeriodType secondaryUsePeriodType,
        @Schema(nullable = true) @Min(1) Integer secondaryUseMonths,
        @Schema(nullable = true) Boolean brandPreReview,

        @Schema(nullable = true) @Size(max = 500) String note,

        @Schema(description = "상품 항목 — 통째 교체된다. 빈 배열이면 전부 삭제다")
        @Valid
        List<Item> items
) {

    @Schema(description = "계약 상품 항목")
    public record Item(

            @Schema(description = "기존 항목 ID — 상세 조회로 받은 값을 그대로 돌려보낸다. 새 행이면 null. "
                    + "「상품을 바꾼 행은 공구가·리워드율·최소 물량이 초기화된다」(§25-5-3)를 서버도 집행하는데, "
                    + "행 식별자가 없으면 중간 행을 지웠을 때 뒤 행이 밀려 올라온 것을 상품 변경으로 오인해 "
                    + "사용자가 보고 있는 값을 지운다", nullable = true)
            Long contractItemId,

            @Schema(description = "상품 ID — 미선택 행도 저장할 수 있다", nullable = true)
            Long productId,

            @Schema(description = "공구가 — 10원 단위", nullable = true)
            @Min(0) @Max(100_000_000)
            Integer groupBuyPrice,

            @Schema(description = "리워드율(%) — 0.0~90.0, 소수점 첫째 자리까지", example = "15.0", nullable = true)
            @DecimalMin("0.0") @DecimalMax("90.0")
            BigDecimal rewardRate,

            @Schema(nullable = true) @Min(0) Integer minQuantity
    ) {
    }
}
