package showroomz.api.admin.product.inspection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import showroomz.domain.product.type.ProductInspectionStatus;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "상품 검수 목록 검색 조건")
public class ProductInspectionSearchCondition {

    @Schema(description = "검수 상태 (미지정 시 전체)", example = "WAITING")
    private ProductInspectionStatus inspectionStatus;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "등록일 시작 (inclusive)", example = "2026-01-01")
    private LocalDate createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "등록일 종료 (inclusive)", example = "2026-12-31")
    private LocalDate createdTo;

    @Schema(description = "검색어 (상품명, 상품번호, 판매자코드, 마켓명)", example = "SRZ")
    private String keyword;

    @Schema(description = "마켓 ID", example = "1")
    private Long marketId;
}
