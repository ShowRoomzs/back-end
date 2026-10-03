package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.DeliveryCarrier;

import java.util.List;

/**
 * 엑셀 일괄 업로드 검증(E3 · §34-5) — <b>상태를 바꾸지 않는다.</b> 「N건 목록에 채우기」는 FE 가 이 응답으로 셀만 채우고,
 * 확정은 수기 입력과 같은 {@code POST /shipments}다(확정 지점이 하나). 부분 성공 허용 — 오류는 행 번호·주문번호·사유.
 */
public record ShipmentParseResponse(int totalRows, int validRows, List<Row> rows) {

    public record Row(
            int rowNumber,
            String orderNumber,
            String subOrderNumber,
            @Schema(description = "매칭된 하위주문 — 오류 행은 null") Long deliveryGroupId,
            DeliveryCarrier carrier,
            String trackingNumber,
            boolean valid,
            @Schema(description = "ALREADY_SHIPPED · INVOICE_DUPLICATE · ORDER_NOT_FOUND · NEW_NOT_ALLOWED · AMBIGUOUS_ORDER · CARRIER_INVALID · STATE_INVALID") String errorCode,
            String message
    ) {
    }
}
