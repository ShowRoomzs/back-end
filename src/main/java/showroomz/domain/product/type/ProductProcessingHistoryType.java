package showroomz.domain.product.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductProcessingHistoryType {
    PRODUCT_CREATED("상품 등록"),
    PRODUCT_INFO_UPDATED("브랜드가 상품 정보 수정"),
    STOCK_UPDATED("재고 수량 수정"),
    HIDDEN("미진열 처리"),
    REDISPLAYED("다시 진열"),
    HIDE_REQUESTED("미진열 요청"),
    PENDING_REVIEW("재검토 대기");

    private final String description;
}
