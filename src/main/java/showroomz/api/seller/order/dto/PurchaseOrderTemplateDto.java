package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import showroomz.domain.order.type.PurchaseOrderColumn;

import java.util.List;

public class PurchaseOrderTemplateDto {

    /** 저장 구성 + 선택 가능한 전체 컬럼 — FE 가 코드로 체크박스·드래그 정렬을 그린다. */
    public record Response(
            @Schema(description = "저장된(없으면 기본 8종) 구성 — 순서가 곧 열 순서") List<PurchaseOrderColumn> columns,
            List<Available> available
    ) {
        public record Available(PurchaseOrderColumn code, String header, boolean basic) {
        }
    }

    public record UpdateRequest(@NotEmpty List<PurchaseOrderColumn> columns) {
    }
}
