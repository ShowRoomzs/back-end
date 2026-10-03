package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import showroomz.domain.order.type.OrderDateBasis;
import showroomz.domain.order.type.OrderSearchType;
import showroomz.domain.order.type.OrderTab;
import showroomz.domain.order.type.PurchaseOrderColumn;

import java.time.LocalDate;
import java.util.List;

/**
 * 발주서 다운로드(E1 · §34-4) — 대상은 선택 건, 선택 없이 열면 현재 탭 전체(목록과 같은 필터를 함께 보낸다).
 * 컬럼 선택 순서 = 엑셀 좌→우 열 순서다.
 */
public record PurchaseOrderRequest(
        @Schema(description = "선택 건 — 비어 있으면 아래 필터(현재 탭 전체)로 대상을 정한다",
                example = "[1024, 1025]", nullable = true) List<Long> deliveryGroupIds,
        @Schema(description = "엑셀 열 구성 — 필수. 배열 순서 = 좌→우 열 순서 · 중복은 첫 위치만 남는다",
                example = "[\"ORDER_NUMBER\", \"RECIPIENT\", \"PHONE\", \"ZIP_CODE\", \"ADDRESS\", \"PRODUCT_NAME\", \"OPTION\", \"QUANTITY\"]")
        @NotEmpty List<PurchaseOrderColumn> columns,
        @Schema(description = "다운로드와 함께 준비 시작 처리 — 생략 시 ON. OFF 면 다운로드만(견적·재고 확인용)",
                example = "true", nullable = true) Boolean startPreparation,
        @Schema(description = "이 구성을 기본값으로 저장 — 생략 시 저장하지 않음", example = "false", nullable = true)
        Boolean saveAsDefault,
        // ── 선택 없이 열었을 때의 대상 필터(목록과 동일) ──
        @Schema(description = "대상 탭 — `deliveryGroupIds`가 비었을 때만 쓴다. **생략 시 `NEW`**(목록 API 기본값 `ALL`과 다르다)",
                example = "NEW", nullable = true) OrderTab tab,
        @Schema(description = "조회 기준일 — 생략 시 `PAID`", example = "PAID", nullable = true) OrderDateBasis dateBasis,
        @Schema(description = "조회 시작일 — 생략 시 탭 기본 기간", example = "2026-09-26", nullable = true) LocalDate from,
        @Schema(description = "조회 종료일 — 생략 시 오늘", example = "2026-10-03", nullable = true) LocalDate to,
        @Schema(description = "검색 대상", example = "PRODUCT_NAME", nullable = true) OrderSearchType searchType,
        @Schema(description = "검색어", example = "글로우 크림", nullable = true) String keyword
) {

    public boolean startPreparationOrDefault() {
        return startPreparation == null || startPreparation;
    }
}
