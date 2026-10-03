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
        @Schema(description = "선택 건 — 비어 있으면 아래 필터(현재 탭 전체)로 대상을 정한다") List<Long> deliveryGroupIds,
        @NotEmpty List<PurchaseOrderColumn> columns,
        @Schema(description = "다운로드와 함께 준비 시작 처리 — 기본 ON. OFF 면 다운로드만(견적·재고 확인용)") Boolean startPreparation,
        @Schema(description = "이 구성을 기본값으로 저장") Boolean saveAsDefault,
        // ── 선택 없이 열었을 때의 대상 필터(목록과 동일) ──
        OrderTab tab,
        OrderDateBasis dateBasis,
        LocalDate from,
        LocalDate to,
        OrderSearchType searchType,
        String keyword
) {

    public boolean startPreparationOrDefault() {
        return startPreparation == null || startPreparation;
    }
}
