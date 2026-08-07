package showroomz.api.admin.product.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.product.type.ProductListSortType;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "관리자 상품 목록 검색 조건")
public class AdminProductSearchCondition {

    @Schema(
            description = "진열 상태 필터 (DISPLAY: 진열, HIDDEN: 미진열, PENDING_REVIEW: 재검토 대기, HIDE_REQUEST: 미진열 요청, 미입력 시 전체)",
            example = "DISPLAY",
            allowableValues = {"DISPLAY", "HIDDEN", "PENDING_REVIEW", "HIDE_REQUEST"}
    )
    private ProductDisplayStatus displayStatus;

    @Schema(
            description = "공구 상태 필터 (PREPARING: 준비중, READY: 준비완료, IN_PROGRESS: 진행중, NOT_CONNECTED: 연결없음, 미입력 시 전체)",
            example = "PREPARING",
            allowableValues = {"PREPARING", "READY", "IN_PROGRESS", "NOT_CONNECTED"}
    )
    private ProductGroupBuyStatus groupBuyStatus;

    @Schema(description = "검색어 (상품명, 상품번호, 브랜드명 부분 일치)", example = "쇼룸즈")
    private String keyword;

    @Schema(
            description = "정렬 (CREATED_AT: 등록일순, MODIFIED_AT: 수정일순, STOCK_ASC: 재고 적은순, 미입력 시 CREATED_AT)",
            example = "CREATED_AT",
            allowableValues = {"CREATED_AT", "MODIFIED_AT", "STOCK_ASC"}
    )
    private ProductListSortType sortType;
}
