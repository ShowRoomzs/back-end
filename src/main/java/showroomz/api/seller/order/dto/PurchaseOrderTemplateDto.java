package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import showroomz.domain.order.type.PurchaseOrderColumn;

import java.util.List;

public class PurchaseOrderTemplateDto {

    /** 저장 구성 + 선택 가능한 전체 컬럼 — FE 가 코드로 체크박스·드래그 정렬을 그린다. */
    @Schema(name = "PurchaseOrderTemplateResponse")
    public record Response(
            @Schema(description = "저장된(없으면 기본 8종) 구성 — 순서가 곧 열 순서",
                    example = "[\"ORDER_NUMBER\", \"RECIPIENT\", \"PHONE\", \"ZIP_CODE\", \"ADDRESS\", \"PRODUCT_NAME\", \"OPTION\", \"QUANTITY\"]")
            List<PurchaseOrderColumn> columns,
            @Schema(description = "선택 가능한 전체 컬럼 13종 — enum 선언 순서") List<Available> available
    ) {
        @Schema(name = "PurchaseOrderColumnOption")
        public record Available(
                @Schema(description = "컬럼 코드", example = "ORDER_NUMBER") PurchaseOrderColumn code,
                @Schema(description = "엑셀 헤더 문구", example = "주문번호") String header,
                @Schema(description = "기본 8종 여부 — 저장 구성이 없을 때 미리 선택된다", example = "true") boolean basic
        ) {
        }
    }

    @Schema(name = "PurchaseOrderTemplateUpdateRequest")
    public record UpdateRequest(
            @Schema(description = "저장할 구성 — 필수. 배열 순서 = 열 순서 · 중복은 첫 위치만 남는다",
                    example = "[\"ORDER_NUMBER\", \"RECIPIENT\", \"PHONE\", \"ADDRESS\", \"PRODUCT_NAME\", \"OPTION\", \"QUANTITY\", \"DELIVERY_MEMO\"]")
            @NotEmpty List<PurchaseOrderColumn> columns
    ) {
    }
}
