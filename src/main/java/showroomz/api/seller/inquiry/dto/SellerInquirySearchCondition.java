package showroomz.api.seller.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.seller.inquiry.type.InquiryVisibility;
import showroomz.api.seller.inquiry.type.SellerInquirySort;
import showroomz.api.seller.inquiry.type.SellerInquiryStatusFilter;
import showroomz.domain.inquiry.type.ProductInquiryType;

import java.util.List;

/**
 * 목록 검색 조건 (§23-2).
 * 상태는 배타적 단일선택이라 탭({@code status})으로, 겹쳐 걸 수 있는 축은 필터 패널로 받는다.
 * 미선택이 곧 전체이므로 필터 패널 쪽에는 `전체` 값이 없다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SellerInquirySearchCondition {

    @Schema(description = "상태 탭 — 배타적 단일선택", example = "WAITING",
            allowableValues = {"ALL", "WAITING", "ANSWERED", "DELETE_REQUESTED", "DELETED"})
    private SellerInquiryStatusFilter status = SellerInquiryStatusFilter.ALL;

    @Schema(description = "문의 유형 필터 — 다중선택. 미선택이 곧 전체",
            example = "[\"OPTION\", \"RESTOCK\"]")
    private List<ProductInquiryType> types;

    @Schema(description = "공개여부 필터 — 다중선택. 미선택이 곧 전체",
            example = "[\"SECRET\"]")
    private List<InquiryVisibility> visibilities;

    @Schema(description = "검색어 — 상품명·질문 통합 검색", example = "유통기한")
    private String keyword;

    @Schema(description = "정렬", example = "WAITING_FIRST", allowableValues = {"WAITING_FIRST", "CREATED_AT"})
    private SellerInquirySort sort = SellerInquirySort.WAITING_FIRST;
}
