package showroomz.domain.product.type;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(
        description = "상품 처리 이력 유형",
        allowableValues = {
                "PRODUCT_CREATED",
                "PRODUCT_INFO_UPDATED",
                "STOCK_UPDATED",
                "HIDDEN",
                "REDISPLAYED",
                "HIDE_REQUESTED",
                "PENDING_REVIEW"
        }
)
public enum ProductProcessingHistoryType {
    @Schema(description = "상품 등록")
    PRODUCT_CREATED("상품 등록"),

    @Schema(description = "브랜드가 상품 정보 수정")
    PRODUCT_INFO_UPDATED("브랜드가 상품 정보 수정"),

    @Schema(description = "재고 수량 수정")
    STOCK_UPDATED("재고 수량 수정"),

    @Schema(description = "미진열 처리")
    HIDDEN("미진열 처리"),

    @Schema(description = "다시 진열")
    REDISPLAYED("다시 진열"),

    @Schema(description = "미진열 요청")
    HIDE_REQUESTED("미진열 요청"),

    @Schema(description = "재검토 대기")
    PENDING_REVIEW("재검토 대기");

    private final String description;
}
