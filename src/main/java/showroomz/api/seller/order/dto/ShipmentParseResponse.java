package showroomz.api.seller.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.order.type.DeliveryCarrier;

import java.util.List;

/**
 * 엑셀 일괄 업로드 검증(E3 · §34-5) — <b>상태를 바꾸지 않는다.</b> 「N건 목록에 채우기」는 FE 가 이 응답으로 셀만 채우고,
 * 확정은 수기 입력과 같은 {@code POST /shipments}다(확정 지점이 하나). 부분 성공 허용 — 오류는 행 번호·주문번호·사유.
 */
public record ShipmentParseResponse(
        @Schema(description = "읽은 행 수 — 헤더·빈 행 제외", example = "3") int totalRows,
        @Schema(description = "등록 가능 행 수 — 「N건 목록에 채우기」의 N", example = "1") int validRows,
        @Schema(description = "행별 분류 결과 — 엑셀 행 순서") List<Row> rows
) {

    @Schema(name = "ShipmentParseRow")
    public record Row(
            @Schema(description = "엑셀 행 번호 — 1부터 · 헤더 포함(첫 데이터 행 = 2)", example = "2") int rowNumber,
            @Schema(description = "주문번호 — 정상 행은 매칭된 주문번호, 오류 행은 엑셀에 적힌 값 그대로", example = "20261003-000123")
            String orderNumber,
            @Schema(description = "매칭된 하위주문번호 — 오류 행은 null", example = "20261003-000123-01", nullable = true)
            String subOrderNumber,
            @Schema(description = "매칭된 하위주문 — 오류 행은 null", example = "1024", nullable = true) Long deliveryGroupId,
            @Schema(description = "택배사 — 한글명·코드 모두 인식. 칸이 비었으면 null(FE 셀에서 고른 뒤 확정한다)",
                    example = "CJ", nullable = true) DeliveryCarrier carrier,
            @Schema(description = "송장번호 — 숫자만 남긴 값", example = "640012345678") String trackingNumber,
            @Schema(description = "등록 가능 여부", example = "true") boolean valid,
            @Schema(description = "ALREADY_SHIPPED · INVOICE_DUPLICATE · ORDER_NOT_FOUND · TRACKING_REQUIRED · NEW_NOT_ALLOWED · "
                    + "AMBIGUOUS_ORDER · CARRIER_INVALID · CANCEL_REQUEST_PENDING · STATE_INVALID — 정상 행은 null",
                    example = "NEW_NOT_ALLOWED", nullable = true) String errorCode,
            @Schema(description = "오류 안내 문구 — 정상 행은 null",
                    example = "신규(준비 대기) 주문 · 준비 시작 전이라 송장을 등록할 수 없습니다.", nullable = true) String message
    ) {
    }
}
